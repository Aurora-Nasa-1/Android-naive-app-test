/// 曲风列表
/// 对应 Node.js module/style_list.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 曲风列表
    /// 对应 /style/list
    pub async fn style_list(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/tag/list/get",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
