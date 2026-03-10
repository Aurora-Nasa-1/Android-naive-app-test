/// 云贝
/// 对应 Node.js module/yunbei.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 云贝数量
    /// 对应 /yunbei
    pub async fn yunbei(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/point/signed/get",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
