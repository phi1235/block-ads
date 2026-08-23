package cert

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"math/big"
	"net"
	"sync"
	"time"
)

// CertManager quản lý Root CA và cấp phát chứng chỉ động (On-the-fly) cho các domain
type CertManager struct {
	caCert     *x509.Certificate
	caKey      *ecdsa.PrivateKey
	caPEM      []byte
	cache      map[string]*tls.Certificate
	cacheMutex sync.RWMutex
}

// NewCertManager khởi tạo hoặc sinh mới một Root CA (ECDSA P-256)
func NewCertManager() (*CertManager, error) {
	privKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, fmt.Errorf("failed to generate CA private key: %w", err)
	}

	serialNumberLimit := new(big.Int).Lsh(big.NewInt(1), 128)
	serialNumber, err := rand.Int(rand.Reader, serialNumberLimit)
	if err != nil {
		return nil, fmt.Errorf("failed to generate serial number: %w", err)
	}

	caTemplate := &x509.Certificate{
		SerialNumber: serialNumber,
		Subject: pkix.Name{
			Organization:  []string{"ReelGuard Security"},
			CommonName:    "ReelGuard Root CA",
			Country:       []string{"VN"},
			Province:      []string{"HoChiMinh"},
			Locality:      []string{"HoChiMinh"},
		},
		NotBefore:             time.Now().Add(-24 * time.Hour),
		NotAfter:              time.Now().Add(10 * 365 * 24 * time.Hour), // 10 năm
		KeyUsage:              x509.KeyUsageCertSign | x509.KeyUsageCRLSign,
		BasicConstraintsValid: true,
		IsCA:                  true,
		MaxPathLen:            1,
	}

	caBytes, err := x509.CreateCertificate(rand.Reader, caTemplate, caTemplate, &privKey.PublicKey, privKey)
	if err != nil {
		return nil, fmt.Errorf("failed to create CA certificate: %w", err)
	}

	parsedCA, err := x509.ParseCertificate(caBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to parse generated CA: %w", err)
	}

	caPEMBlock := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: caBytes})

	return &CertManager{
		caCert: parsedCA,
		caKey:  privKey,
		caPEM:  caPEMBlock,
		cache:  make(map[string]*tls.Certificate),
	}, nil
}

// GetRootCAPEM trả về chứng chỉ Root CA ở định dạng PEM để người dùng cài đặt vào Android
func (cm *CertManager) GetRootCAPEM() []byte {
	return cm.caPEM
}

// GetCertificateForHost ký động chứng chỉ SSL cho domain được yêu cầu
func (cm *CertManager) GetCertificateForHost(host string) (*tls.Certificate, error) {
	// Tách hostname nếu có kèm port (vd: graph.facebook.com:443)
	h, _, err := net.SplitHostPort(host)
	if err == nil {
		host = h
	}

	cm.cacheMutex.RLock()
	if cert, ok := cm.cache[host]; ok {
		cm.cacheMutex.RUnlock()
		return cert, nil
	}
	cm.cacheMutex.RUnlock()

	cm.cacheMutex.Lock()
	defer cm.cacheMutex.Unlock()

	// Double check cache
	if cert, ok := cm.cache[host]; ok {
		return cert, nil
	}

	// Tạo private key cho host
	hostKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, err
	}

	serialNumberLimit := new(big.Int).Lsh(big.NewInt(1), 128)
	serialNumber, err := rand.Int(rand.Reader, serialNumberLimit)
	if err != nil {
		return nil, err
	}

	template := &x509.Certificate{
		SerialNumber: serialNumber,
		Subject: pkix.Name{
			Organization: []string{"ReelGuard Dynamic"},
			CommonName:   host,
		},
		NotBefore:             time.Now().Add(-1 * time.Hour),
		NotAfter:              time.Now().Add(365 * 24 * time.Hour),
		KeyUsage:              x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
	}

	if ip := net.ParseIP(host); ip != nil {
		template.IPAddresses = append(template.IPAddresses, ip)
	} else {
		template.DNSNames = append(template.DNSNames, host)
	}

	certBytes, err := x509.CreateCertificate(rand.Reader, template, cm.caCert, &hostKey.PublicKey, cm.caKey)
	if err != nil {
		return nil, fmt.Errorf("failed to sign host certificate: %w", err)
	}

	tlsCert := &tls.Certificate{
		Certificate: [][]byte{certBytes, cm.caCert.Raw},
		PrivateKey:  hostKey,
	}

	cm.cache[host] = tlsCert
	return tlsCert, nil
}
