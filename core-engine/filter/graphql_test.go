package filter

import (
	"encoding/json"
	"testing"
)

func TestFilterGraphQLResponse_PreservesPaginationAndStripsAds(t *testing.T) {
	// Giả lập response thực tế từ Facebook GraphQL chứa 3 Reels (2 video thật, 1 video quảng cáo)
	rawFBResponse := `{
		"data": {
			"viewer": {
				"fb_shorts_viewer_connection": {
					"edges": [
						{
							"node": {
								"id": "reel_video_001",
								"is_sponsored": false,
								"video": {
									"id": "vid_111",
									"playable_url": "https://video.xx.fbcdn.net/v/reel1.mp4"
								}
							}
						},
						{
							"node": {
								"id": "ad_reel_002",
								"is_sponsored": true,
								"sponsored_data": {
									"ad_id": "987654321",
									"client_token": "token_abc"
								},
								"video": {
									"id": "vid_ad",
									"playable_url": "https://video.xx.fbcdn.net/v/ad_video.mp4"
								}
							}
						},
						{
							"node": {
								"id": "reel_video_003",
								"is_sponsored": false,
								"video": {
									"id": "vid_333",
									"playable_url": "https://video.xx.fbcdn.net/v/reel3.mp4"
								}
							}
						}
					],
					"page_info": {
						"end_cursor": "CURSOR_AQHR_NEXT_PAGE_TOKEN_12345",
						"has_next_page": true,
						"start_cursor": "CURSOR_START_001"
					}
				}
			}
		}
	}`

	cleanedBytes, adsBlocked := FilterGraphQLResponse([]byte(rawFBResponse))

	if adsBlocked != 1 {
		t.Fatalf("Expected 1 ad blocked, got %d", adsBlocked)
	}

	var parsed map[string]interface{}
	if err := json.Unmarshal(cleanedBytes, &parsed); err != nil {
		t.Fatalf("Failed to parse cleaned JSON: %v", err)
	}

	// 1. Kiểm tra mảng edges chỉ còn 2 video
	data := parsed["data"].(map[string]interface{})
	viewer := data["viewer"].(map[string]interface{})
	conn := viewer["fb_shorts_viewer_connection"].(map[string]interface{})
	edges := conn["edges"].([]interface{})

	if len(edges) != 2 {
		t.Fatalf("Expected 2 clean edges, got %d", len(edges))
	}

	edge1 := edges[0].(map[string]interface{})["node"].(map[string]interface{})
	edge2 := edges[1].(map[string]interface{})["node"].(map[string]interface{})

	if edge1["id"] != "reel_video_001" || edge2["id"] != "reel_video_003" {
		t.Errorf("Unexpected video IDs in clean stream: %v, %v", edge1["id"], edge2["id"])
	}

	// 2. KIỂM TRA QUAN TRỌNG NHẤT: page_info.end_cursor phải NGUYÊN VẸN 100%
	pageInfo := conn["page_info"].(map[string]interface{})
	if pageInfo["end_cursor"] != "CURSOR_AQHR_NEXT_PAGE_TOKEN_12345" {
		t.Errorf("Expected cursor preserved, got %v", pageInfo["end_cursor"])
	}
	if pageInfo["has_next_page"] != true {
		t.Errorf("Expected has_next_page to be true")
	}
}

func TestFilterGraphQLResponse_CommentsUnchanged(t *testing.T) {
	// Giả lập response lấy bình luận (không có quảng cáo)
	commentsResponse := `{
		"data": {
			"feedback": {
				"comments": {
					"edges": [
						{
							"node": {
								"id": "comment_1",
								"body": { "text": "Video hay qua ban oi!" },
								"is_sponsored": false
							}
						}
					]
				}
			}
		}
	}`

	_, adsBlocked := FilterGraphQLResponse([]byte(commentsResponse))
	if adsBlocked != 0 {
		t.Errorf("Expected 0 ads blocked in comment response, got %d", adsBlocked)
	}
}
