/// 歌曲详情
/// 对应 Node.js module/song_detail.js
use crate::request::{ApiClient, ApiResponse, CryptoType};
use crate::error::Result;
use serde_json::json;
use super::Query;

impl ApiClient {
    /// 歌曲详情
    /// 对应 /song/detail
    pub async fn song_detail(&self, query: &Query) -> Result<ApiResponse> {
        let ids = query.get_or("ids", "");
        let c: Vec<String> = ids
            .split(',')
            .map(|s| s.trim())
            .filter(|s| !s.is_empty())
            .map(|id| format!(r#"{{"id":{}}}"#, id))
            .collect();
        let data = json!({
            "c": format!("[{}]", c.join(","))
        });
        self.request("/api/v3/song/detail", data, query.to_option(CryptoType::Weapi))
            .await
    }
}
