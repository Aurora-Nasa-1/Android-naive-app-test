/// 国家编码列表
/// 对应 Node.js module/countries_code_list.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 国家编码列表
    /// 对应 /countries/code/list
    pub async fn countries_code_list(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request("/api/lbs/countries/v1", data, query.to_option(CryptoType::default()))
            .await
    }
}
