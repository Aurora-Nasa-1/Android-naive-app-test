/// 删除动态
/// 对应 Node.js module/event_del.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 删除动态
    /// 对应 /event/del
    pub async fn event_del(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "id": query.get_or("evId", "0")
        });
        self.request("/api/event/delete", data, query.to_option(CryptoType::Weapi))
            .await
    }
}
