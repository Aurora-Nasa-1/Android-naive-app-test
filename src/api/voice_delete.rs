/// 删除声音
/// 对应 Node.js module/voice_delete.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 删除声音
    /// 对应 /voice/delete
    pub async fn voice_delete(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "ids": query.get_or("ids", "")
        });
        self.request("/api/content/voice/delete", data, query.to_option(CryptoType::Eapi))
            .await
    }
}
