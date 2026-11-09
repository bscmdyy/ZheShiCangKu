# 发布到 GitHub（开源步骤）

本机没有安装 git，也不能联网推送，所以下面是你手动执行的步骤（文件已经整理好，
在 `dist/open-source/tlmformation/`，压缩包：`dist/tlmformation-9.3-open-source.zip`）。

## 1. 安装 git

- Windows：<https://git-scm.com/download/win>（安装时选 "Git from the command line"）
- 安装后新开一个 PowerShell，执行 `git --version` 确认。

## 2. 在 GitHub 创建仓库

1. 登录 GitHub → 右上角 `+` → **New repository**
2. Repository name：`tlmformation`（或你喜欢的名字）
3. 可见性：**Public**
4. **不要**勾选 "Add a README" / .gitignore / license（我们已经有文件）
5. 创建后会显示一个空仓库页面，记下地址，例如 `https://github.com/<你的用户名>/tlmformation.git`

## 3. 把整理好的源码推上去

```powershell
# 进入整理好、没有构建产物的源码目录
cd <工作区>\dist\open-source\tlmformation

# 初始化并提交
git init
git add .
git commit -m "tlmformation 9.3: open source (GPL-3.0)"

# 关联远程仓库（换成你自己的地址）
git branch -M main
git remote add origin https://github.com/<你的用户名>/tlmformation.git
git push -u origin main
```

如果提示登录：用 GitHub 的 **Personal Access Token**（Settings → Developer settings →
Personal access tokens → Generate new token，勾选 `repo`）作为密码，或安装 GitHub Desktop/CLI 登录。

## 4. 发布可下载的 jar（Releases）

1. 仓库页 → **Releases** → **Draft a new release**
2. Tag：`v9.3`，标题：`tlmformation 9.3`
3. 上传 `dist/tlmformation-9.3-rappel.jar`
4. **同时把 GitHub 自动生成的 "Source code (zip)" 留着**（GPL-3.0 要求提供对应源码）
5. 说明里写清依赖版本（见 README「环境」表）

## 5. 需要遵守的许可证要求（重要）

- 本项目因依赖/分发 **SuperbWarfare（GPL-3.0）** 而必须整体 **GPL-3.0**：
  - 仓库必须包含 `LICENSE`（已放入 GPL-3.0 全文）与 `THIRD_PARTY_NOTICES.md`；
  - 发布 jar 时必须同时提供对应版本的**源码**（GitHub Release 的 Source code 或仓库 tag 即可）；
  - 不得给代码再加额外限制（例如禁止商用、禁止修改），GPL-3.0 不允许；
  - 修改后的源码也必须在发布时提供。
- 若你以后想改成 MIT 等宽松协议：必须**移除对 SuperbWarfare 的代码依赖与分发**（包括继承
  `VehicleEntity`、车内数据/音效/模型等），否则不能换协议。
- 车万女仆的美术资源是 **CC BY-NC-SA 4.0（禁止商用）**：本模组已不再分发其任何资源，
  只调用其公开 API；后续请勿把它的贴图/音效复制进本仓库。
- 若将来商业化（收费/赞助门槛），务必先清理 GPL/NC 相关内容。

## 6. 构建（给其他开发者）

```powershell
# JDK 17
# 把依赖 jar 放进 libs/（仓库不提交依赖，见 libs/README.md）
gradlew.bat build
# 产物：build/libs/tlmformation-9.3.jar
```

## 7. 可选：CI（GitHub Actions）

可以加 `.github/workflows/build.yml`，在 push 时自动构建；依赖 jar 不能提交，
需要用 `wget`/`curl` 从 Modrinth/CurseForge 下载（注意各自动分发条款）。
