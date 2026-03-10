/// 消息计数
/// 对应 Node.js module/pl_count.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 消息计数
    /// 对应 /pl/count
    pub async fn pl_count(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request("/api/pl/count", data, query.to_option(CryptoType::Weapi))
            .await
    }
}
