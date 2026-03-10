/// 曲风偏好
/// 对应 Node.js module/style_preference.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 曲风偏好
    /// 对应 /style/preference
    pub async fn style_preference(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/tag/my/preference/get",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
