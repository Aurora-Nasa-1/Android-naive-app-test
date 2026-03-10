/// 推荐 MV
/// 对应 Node.js module/personalized_mv.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 推荐 MV
    /// 对应 /personalized/mv
    pub async fn personalized_mv(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/personalized/mv",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
