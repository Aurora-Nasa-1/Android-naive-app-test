/// 签到快乐信息
/// 对应 Node.js module/sign_happy_info.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 签到快乐信息
    /// 对应 /sign/happy/info
    pub async fn sign_happy_info(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/sign/happy/info",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
