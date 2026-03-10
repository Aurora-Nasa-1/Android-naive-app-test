/// 获取音乐人VIP任务
/// 对应 Node.js module/musician_vip_tasks.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 获取音乐人VIP任务
    /// 对应 /musician/vip/tasks
    pub async fn musician_vip_tasks(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/nmusician/workbench/special/right/vip/info",
            data,
            query.to_option(CryptoType::Eapi),
        )
        .await
    }
}
