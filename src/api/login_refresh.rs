/// 刷新登录
/// 对应 Node.js module/login_refresh.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 刷新登录
    /// 对应 /login/refresh
    pub async fn login_refresh(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/login/token/refresh",
            data,
            query.to_option(CryptoType::default()),
        )
        .await
    }
}
