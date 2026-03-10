/// 黑胶乐签打卡
/// 对应 Node.js module/vip_sign.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 黑胶乐签打卡
    /// 对应 /vip/sign
    pub async fn vip_sign(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/vip-center-bff/task/sign",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
