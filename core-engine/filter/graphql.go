package filter

import (
	"encoding/json"
	"strings"
)

// FilterStats ghi nhận thống kê số lượng quảng cáo đã chặn trong phiên
type FilterStats struct {
	TotalAdsBlocked int
}

// FilterGraphQLResponse xử lý JSON payload trả về từ Facebook GraphQL.
// Trả về JSON sạch đã loại bỏ các node quảng cáo nhưng GIỮ NGUYÊN page_info (con trỏ phân trang).
func FilterGraphQLResponse(body []byte) ([]byte, int) {
	if len(body) == 0 {
		return body, 0
	}

	var root interface{}
	if err := json.Unmarshal(body, &root); err != nil {
		// Không phải JSON hợp lệ -> Trả về nguyên bản
		return body, 0
	}

	adsBlocked := 0
	cleanedRoot := cleanJSONRecursive(root, &adsBlocked)

	if adsBlocked == 0 {
		return body, 0
	}

	cleanedBytes, err := json.Marshal(cleanedRoot)
	if err != nil {
		return body, 0
	}

	return cleanedBytes, adsBlocked
}

// cleanJSONRecursive đệ quy qua cây JSON để tìm và lọc các mảng edges chứa Reels/Feed
func cleanJSONRecursive(node interface{}, adsBlocked *int) interface{} {
	switch val := node.(type) {
	case map[string]interface{}:
		result := make(map[string]interface{}, len(val))
		for k, v := range val {
			if k == "edges" {
				if edgesArr, ok := v.([]interface{}); ok {
					cleanEdges := make([]interface{}, 0, len(edgesArr))
					for _, edgeItem := range edgesArr {
						if isSponsoredEdge(edgeItem) {
							*adsBlocked++
							continue // Bỏ qua quảng cáo
						}
						// Nếu không phải quảng cáo, tiếp tục làm sạch các object con bên trong
						cleanEdges = append(cleanEdges, cleanJSONRecursive(edgeItem, adsBlocked))
					}
					result[k] = cleanEdges
					continue
				}
			}
			result[k] = cleanJSONRecursive(v, adsBlocked)
		}
		return result

	case []interface{}:
		result := make([]interface{}, 0, len(val))
		for _, item := range val {
			result = append(result, cleanJSONRecursive(item, adsBlocked))
		}
		return result

	default:
		return val
	}
}

// isSponsoredEdge kiểm tra xem một phần tử trong mảng edges có phải là Reels/Story/Video quảng cáo hay không
func isSponsoredEdge(edge interface{}) bool {
	edgeMap, ok := edge.(map[string]interface{})
	if !ok {
		return false
	}

	nodeObj, hasNode := edgeMap["node"]
	if !hasNode {
		return false
	}

	nodeMap, ok := nodeObj.(map[string]interface{})
	if !ok {
		return false
	}

	// 1. Kiểm tra cờ is_sponsored boolean
	if isSponsored, ok := nodeMap["is_sponsored"].(bool); ok && isSponsored {
		return true
	}

	// 2. Kiểm tra có object sponsored_data hay không
	if sponsoredData, ok := nodeMap["sponsored_data"]; ok && sponsoredData != nil {
		return true
	}

	// 3. Kiểm tra typename có liên quan đến quảng cáo
	if typeName, ok := nodeMap["__typename"].(string); ok {
		lowerType := strings.ToLower(typeName)
		if strings.Contains(lowerType, "sponsored") || strings.Contains(lowerType, "advertisement") || strings.Contains(lowerType, "adunit") {
			return true
		}
	}

	// 4. Kiểm tra tracking_codes (thường gắn với FB Ads)
	if tracking, ok := nodeMap["tracking_codes"]; ok && tracking != nil {
		if trackingArr, ok := tracking.([]interface{}); ok && len(trackingArr) > 0 {
			// Thường nếu có sponsored_data hoặc là Ad thì tracking codes sẽ chứa chuỗi ad
			for _, t := range trackingArr {
				if tStr, ok := t.(string); ok {
					if strings.Contains(tStr, "ad_id") || strings.Contains(tStr, "client_token") {
						return true
					}
				}
			}
		}
	}

	// 5. Kiểm tra call_to_action (Cài đặt ngay, Mua ngay, Tìm hiểu thêm trong video ads)
	if cta, ok := nodeMap["call_to_action"]; ok && cta != nil {
		if isSponsored, ok := nodeMap["is_ad"].(bool); ok && isSponsored {
			return true
		}
	}

	return false
}
