/// 最近联系
/// 对应 Node.js module/msg_recentcontact.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 最近联系
    /// 对应 /msg/recentcontact
    pub async fn msg_recentcontact(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/msg/recentcontact/get",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
