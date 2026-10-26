// MillionOS for Windows and Mac: the same MillionOS page (built into ../dist) in the system's web view.
// On start it looks for a newer GitHub Release; if there is one it installs it and reopens, keeping your career.
use tauri_plugin_updater::UpdaterExt;

pub fn run() {
  tauri::Builder::default()
    .plugin(tauri_plugin_updater::Builder::new().build())
    .setup(|app| {
      let handle = app.handle().clone();
      tauri::async_runtime::spawn(async move {
        // Offline, or no newer version: carry on.
        let _ = update(handle).await;
      });
      Ok(())
    })
    .run(tauri::generate_context!())
    .expect("error while running MillionOS");
}

async fn update(app: tauri::AppHandle) -> tauri_plugin_updater::Result<()> {
  if let Some(update) = app.updater()?.check().await? {
    update.download_and_install(|_, _| {}, || {}).await?;
    app.restart();
  }
  Ok(())
}
