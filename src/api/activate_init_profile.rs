/// 初始化名字
/// 对应 Node.js module/activate_init_profile.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 初始化名字
    /// 对应 /activate/init/profile
    pub async fn activate_init_profile(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "nickname": query.get("nickname").unwrap_or("")
        });
        self.request(
            "/api/activate/initProfile",
            data,
            query.to_option(CryptoType::default()),
        )
        .await
    }
}
