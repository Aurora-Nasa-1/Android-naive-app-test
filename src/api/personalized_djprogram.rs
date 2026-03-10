/// 推荐电台
/// 对应 Node.js module/personalized_djprogram.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 推荐电台
    /// 对应 /personalized/djprogram
    pub async fn personalized_djprogram(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/personalized/djprogram",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
