/// 独家放送
/// 对应 Node.js module/personalized_privatecontent.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 独家放送
    /// 对应 /personalized/privatecontent
    pub async fn personalized_privatecontent(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/personalized/privatecontent",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
