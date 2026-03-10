/// 一起听 当前列表获取
/// 对应 Node.js module/listentogether_sync_playlist_get.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 一起听 当前列表获取
    /// 对应 /listentogether/sync/playlist/get
    pub async fn listentogether_sync_playlist_get(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({
            "roomId": query.get_or("roomId", "")
        });
        self.request(
            "/api/listen/together/sync/playlist/get",
            data,
            query.to_option(CryptoType::Eapi),
        )
        .await
    }
}
