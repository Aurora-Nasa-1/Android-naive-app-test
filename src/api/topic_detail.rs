/// 话题详情
/// 对应 Node.js module/topic_detail.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 话题详情
    /// 对应 /topic/detail
    pub async fn topic_detail(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "actid": query.get_or("actid", "")
        });
        self.request(
            "/api/act/detail",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
