/// 视频详情
/// 对应 Node.js module/video_detail.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 视频详情
    /// 对应 /video/detail
    pub async fn video_detail(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "id": query.get_or("id", "0")
        });
        self.request(
            "/api/cloudvideo/v1/video/detail",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
