/// 视频分组列表
/// 对应 Node.js module/video_group_list.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 视频分组列表
    /// 对应 /video/group/list
    pub async fn video_group_list(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/cloudvideo/group/list",
            data,
            query.to_option(CryptoType::Weapi),
        )
        .await
    }
}
