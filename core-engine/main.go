package main

import (
	"bytes"
	"compress/gzip"
	"crypto/tls"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"

	"github.com/reelguard/core-engine/cert"
	"github.com/reelguard/core-engine/filter"
)

type ProxyServer struct {
	certManager *cert.CertManager
	port        int
}

func NewProxyServer(port int) (*ProxyServer, error) {
	cm, err := cert.NewCertManager()
	if err != nil {
		return nil, fmt.Errorf("failed to init cert manager: %w", err)
	}

	return &ProxyServer{
		certManager: cm,
		port:        port,
	}, nil
}

func (p *ProxyServer) Start() error {
	// Xuất CA cert ra file để cài đặt
	caPEM := p.certManager.GetRootCAPEM()
	if err := os.WriteFile("ReelGuard_Root_CA.crt", caPEM, 0644); err == nil {
		log.Println("[ReelGuard] Đã xuất file chứng chỉ Root CA: ReelGuard_Root_CA.crt")
	}

	server := &http.Server{
		Addr:    fmt.Sprintf(":%d", p.port),
		Handler: http.HandlerFunc(p.handleHTTP),
	}

	log.Printf("[ReelGuard] Proxy Engine đang lắng nghe trên cổng :%d ...\n", p.port)
	return server.ListenAndServe()
}

func (p *ProxyServer) handleHTTP(w http.ResponseWriter, req *http.Request) {
	if req.Method == http.MethodConnect {
		p.handleHTTPSConnect(w, req)
	} else {
		p.handlePlainHTTP(w, req)
	}
}

// handleHTTPSConnect thực hiện bắt tay MITM TLS để giải mã và lọc dữ liệu HTTPS từ Facebook
func (p *ProxyServer) handleHTTPSConnect(w http.ResponseWriter, req *http.Request) {
	host := req.Host

	// Lấy kết nối raw TCP tới client
	hijacker, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "Hijacking not supported", http.StatusInternalServerError)
		return
	}
	clientConn, _, err := hijacker.Hijack()
	if err != nil {
		http.Error(w, err.Error(), http.StatusServiceUnavailable)
		return
	}

	// Báo cho client kết nối tunnel thành công
	clientConn.Write([]byte("HTTP/1.1 200 Connection Established\r\n\r\n"))

	// Ký động chứng chỉ SSL cho domain được gọi
	tlsCert, err := p.certManager.GetCertificateForHost(host)
	if err != nil {
		log.Printf("[TLS Error] Không thể ký chứng chỉ cho %s: %v\n", host, err)
		clientConn.Close()
		return
	}

	// Bắt đầu TLS Server handshake với Client (Facebook App)
	tlsConfig := &tls.Config{
		Certificates: []tls.Certificate{*tlsCert},
	}
	tlsClientConn := tls.Server(clientConn, tlsConfig)
	if err := tlsClientConn.Handshake(); err != nil {
		// Thường gặp nếu app Facebook chưa gỡ SSL Pinning
		log.Printf("[TLS Handshake Failed] Host: %s (Client chưa tin tưởng CA hoặc chưa gỡ SSL Pinning): %v\n", host, err)
		tlsClientConn.Close()
		return
	}

	// Kết nối tới Facebook Server thực tế
	targetConn, err := tls.Dial("tcp", host, &tls.Config{
		ServerName: strings.Split(host, ":")[0],
	})
	if err != nil {
		log.Printf("[Upstream Error] Không thể kết nối tới %s: %v\n", host, err)
		tlsClientConn.Close()
		return
	}

	// Đọc request từ client đã giải mã TLS
	go p.forwardAndFilter(tlsClientConn, targetConn, host)
}

func (p *ProxyServer) forwardAndFilter(clientConn net.Conn, targetConn net.Conn, host string) {
	defer clientConn.Close()
	defer targetConn.Close()

	// Chỉ can thiệp sâu vào traffic của Facebook
	isFacebook := strings.Contains(host, "facebook.com") || strings.Contains(host, "fbcdn.net")

	if !isFacebook {
		// Nếu không phải Facebook -> Forward 2 chiều trực tiếp
		go io.Copy(targetConn, clientConn)
		io.Copy(clientConn, targetConn)
		return
	}

	// Forward client -> target
	go io.Copy(targetConn, clientConn)

	// Đọc response từ Facebook Server về để lọc
	buf := make([]byte, 64*1024)
	for {
		n, err := targetConn.Read(buf)
		if n > 0 {
			rawChunk := buf[:n]

			// Xử lý lọc GraphQL nếu có chứa JSON
			if bytes.Contains(rawChunk, []byte("fb_shorts_viewer_connection")) || bytes.Contains(rawChunk, []byte("is_sponsored")) {
				cleaned, adsCount := filter.FilterGraphQLResponse(rawChunk)
				if adsCount > 0 {
					log.Printf("🛡️ [ReelGuard] Đã phát hiện và gỡ bỏ %d Video Reels Quảng Cáo! (Bảo toàn phân trang)\n", adsCount)
					clientConn.Write(cleaned)
					continue
				}
			}

			clientConn.Write(rawChunk)
		}
		if err != nil {
			break
		}
	}
}

func (p *ProxyServer) handlePlainHTTP(w http.ResponseWriter, req *http.Request) {
	resp, err := http.DefaultTransport.RoundTrip(req)
	if err != nil {
		http.Error(w, err.Error(), http.StatusServiceUnavailable)
		return
	}
	defer resp.Body.Close()

	for k, vv := range resp.Header {
		for _, v := range vv {
			w.Header().Add(k, v)
		}
	}
	w.WriteHeader(resp.StatusCode)

	if strings.Contains(resp.Header.Get("Content-Encoding"), "gzip") {
		gr, err := gzip.NewReader(resp.Body)
		if err == nil {
			defer gr.Close()
			bodyBytes, _ := io.ReadAll(gr)
			cleaned, _ := filter.FilterGraphQLResponse(bodyBytes)
			w.Write(cleaned)
			return
		}
	}

	io.Copy(w, resp.Body)
}

func main() {
	port := flag.Int("port", 8899, "Cổng lắng nghe của Proxy")
	flag.Parse()

	proxy, err := NewProxyServer(*port)
	if err != nil {
		log.Fatalf("Khởi tạo thất bại: %v", err)
	}

	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-c
		log.Println("\n[ReelGuard] Đang dừng server...")
		os.Exit(0)
	}()

	if err := proxy.Start(); err != nil {
		log.Fatalf("Lỗi server: %v", err)
	}
}
