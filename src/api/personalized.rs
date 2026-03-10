/// 个性化推荐歌单
/// 对应 Node.js module/personalized.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 推荐歌单
    /// 对应 /personalized
    pub async fn personalized(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "limit": query.get_or("limit", "30").parse::<i64>().unwrap_or(30),
            "total": true,
            "n": 1000
        });
        self.request(
            "/api/personalized/playlist",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
