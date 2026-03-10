/// 云贝信息
/// 对应 Node.js module/yunbei_info.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 云贝账户信息
    /// 对应 /yunbei/info
    pub async fn yunbei_info(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/v1/user/info",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
