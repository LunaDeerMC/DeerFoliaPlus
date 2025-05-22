# DeerFoliaPlus

DeerFoliaPlus 相比于 DeerFolia 引入了更多额外功能，这些功能并非性能优化方面的改动，而是增强了服务器的功能性。

## 此分支特性

- FakePlayer (Leaves/Lumina)

## 额外配置

## 如何自行编译

1. 克隆本仓库到本地；
2. 在终端执行 `./gradlew applyAllPatches` 应用补丁；
3. 完成后会在项目目录下生成 `deer-folia-server` 和 `deer-folia-api` ，前者即为源码目录;
4. 执行 `./gradlew createMojmapPaperclipJar` ，完成后会在 `deer-folia-server/build/libs` 下生成服务器核心文件；

## 如何添加新补丁

### 对上游代码修改

1. 修改 `deer-folia-server` 或 `deer-folia-api` 中的源码；
2. 在 `deer-folia-server` 或 `deer-folia-api` 目录中将修改内容添加 `git add .` 并提交 `git commit` ，填写补丁信息；
3. 在根目录运行 `./gradlew rebuildAllServerPatches` ，将刚才提交的修改生成为新补丁；

### 新增文件

1. 在 `deer-folia-server/src/main/java` 中新增相关文件；
2. 直接提交 `git add .` 并提交 `git commit` ，填写补丁信息即可；

> 通过将与上游源码无关的新增文件独立开，减少对上游修改 patch 文件的长度使得项目更易于维护。

## 修改已有补丁

修改已有的补丁步骤相对复杂：

### 方法一

这种方法的工作原理是暂时将 `HEAD` 重置为所需的提交，然后使用 `git rebase` 进行编辑。

> ❗ 在编辑过程中，除非您 *同时* 将对应模块重置为相关提交，否则将无法编译。就 API 而言，您必须重新应用 Server 补丁，如果正在编辑
> Server 补丁，则必须重新应用 API 补丁。还要注意的是，这样做时任何一个模块都可能无法编译。这不是一个正常的现象，但这种情况时有发生。请给
> Paper 官方提交 ISSUE ！

1. 在 `deer-folia-server` 或 `deer-folia-api` 目录中执行 `git rebase -i base`
   ，应该会输出 [这样的](https://gist.github.com/zachbr/21e92993cb99f62ffd7905d7b02f3159) 内容。
2. 将你需要修改的补丁由 `pick` 替换为 `edit` 然后保存退出；
   - 一次只能修改 **一个** 文件！
3. 对你需要修改的补丁作出新的修改；
4. 使用 `git add .` 添加补丁，再使用 `git commit --amend` 提交；
   - **确保添加了 `--amend` 选项** 否则将会创建一个新补丁而不是修改原补丁。
   - 此处提交时也可以修改补丁信息。
5. 终端执行 `git rebase --continue` 应用更新；
6. 再在跟项目目录执行 `./gradlew rebuildPatches` 生成新的补丁；

### 方法二

如果你只是在编辑一个较新的提交，或者你的改动很小，那么在 HEAD 上进行改动，然后在测试后移动提交可能会更简单。

这种方法的好处是可以编译测试你的改动，而不必弄乱你的 HEAD。

#### 手动

1. 修改相应位置源码；
2. 提交修改（可以不写提交内容）；
3. 在 `deer-folia-server` 或 `deer-folia-api` 目录中执行 `git rebase -i base` ，将刚才的提交移动到你想要修改的补丁提交下方；
4. 将新提交的 `pick` 修改为如下内容：
   - `f`/`fixup`：将你的新修改合并到补丁内，但不改变补丁信息；
   - `s`/`squash`：将你的新修改合并到补丁内，并用新的补丁信息替换原补丁信息；
5. 在跟项目目录执行 `./gradlew rebuildPatches` 应用补丁更新；

#### 自动

1. 修改相应位置源码；
2. 提交修改内容 `git commit -a --fixup <要修改的补丁 hash 值>`；
   - 如果希望更新补丁信息，你可以使用 `--squash` 替换 `--fixup`；
   - 如果你不知道要修改的补丁 hash 值，你可以使用 `git log` 查看；
   - 如果你只知道补丁的名称，你可以使用 `git log --grep=<补丁名称>` 查看；
3. 执行 `git rebase -i --autosquash base` ，这将会自动将你的修改移动到对应的补丁下方；
4. 在跟项目目录执行 `./gradlew rebuildAllServerPatches` 应用补丁更新；

## 更新上游 Folia 修改

1. 在终端执行 `./gradlew updateFoliaRef` 更新上游 Folia 修改；
2. 应用更新的补丁：`./gradlew applyAllPatches`。
3. 如果存在冲突，解决冲突后执行 `git add .` 将解决完的文件添加到暂存区；
   - 如果遇到 `invalid object` 错误，可以使用 `git apply --reject <patch file>` 手动应用补丁；
   - 会生成 `.rej` 文件，可在其中查看冲突内容，手动解决冲突；
   - 完成后删除 `.rej` 文件，然后执行 `git add .`；
4. 然后运行 `git am --resolved` 继续应用补丁；
5. 如果存在新的冲突，重复步骤 3 和 4；
6. 全部补丁应用完成后，更新补丁：`./gradlew rebuildAllServerPatches`。
