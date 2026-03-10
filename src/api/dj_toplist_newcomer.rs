/// 电台新人榜
/// 对应 Node.js module/dj_toplist_newcomer.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 电台新人榜
    /// 对应 /dj/toplist/newcomer
    pub async fn dj_toplist_newcomer(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "limit": query.get_or("limit", "100").parse::<i64>().unwrap_or(100),
            "offset": query.get_or("offset", "0").parse::<i64>().unwrap_or(0)
        });
        self.request(
            "/api/dj/toplist/newcomer",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
