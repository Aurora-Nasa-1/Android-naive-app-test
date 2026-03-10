/// 音乐人签到
/// 对应 Node.js module/musician_sign.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 音乐人签到
    /// 对应 /musician/sign
    pub async fn musician_sign(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request("/api/creator/user/access", data, query.to_option(CryptoType::Weapi))
            .await
    }
}
