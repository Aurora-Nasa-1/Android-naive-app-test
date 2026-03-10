/// 专辑详情
/// 对应 Node.js module/album.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 专辑详情
    /// 对应 /album
    pub async fn album(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            &format!("/api/v1/album/{}", query.get_or("id", "0")),
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
