/// 退出登录
/// 对应 Node.js module/logout.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 退出登录
    /// 对应 /logout
    pub async fn logout(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request("/api/logout", data, query.to_option(CryptoType::default()))
            .await
    }
}
