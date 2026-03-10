/// 根据昵称获取用户ID
/// 对应 Node.js module/get_userids.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 根据昵称获取用户ID
    /// 对应 /get/userids
    pub async fn get_userids(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "nicknames": query.get("nicknames").unwrap_or("")
        });
        self.request(
            "/api/user/getUserIds",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
