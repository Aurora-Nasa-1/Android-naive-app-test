/// 登录状态
/// 对应 Node.js module/login_status.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 登录状态
    /// 对应 /login/status
    pub async fn login_status(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/w/nuser/account/get",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
