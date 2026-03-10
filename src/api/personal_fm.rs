/// 私人 FM
/// 对应 Node.js module/personal_fm.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 私人 FM
    /// 对应 /personal/fm
    pub async fn personal_fm(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/v1/radio/get",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
