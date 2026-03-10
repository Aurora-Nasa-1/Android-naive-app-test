/// 歌单分类列表
/// 对应 Node.js module/playlist_catlist.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 歌单分类列表
    /// 对应 /playlist/catlist
    pub async fn playlist_catlist(&self, query: &Query) -> Result<ApiResponse> {
        let data = json!({});
        self.request(
            "/api/playlist/catalogue",
            data,
            query.to_option(CryptoType::Eapi),
        )
        .await
    }
}
