/// 曲风详情
/// 对应 Node.js module/style_detail.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 曲风详情
    /// 对应 /style/detail
    pub async fn style_detail(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "tagId": query.get_or("tagId", "")
        });
        self.request(
            "/api/style-tag/home/head",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
