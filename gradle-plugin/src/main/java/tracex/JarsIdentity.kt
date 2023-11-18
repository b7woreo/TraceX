package tracex

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.work.ChangeType
import org.gradle.work.InputChanges
import java.io.File

class JarsIdentity(
  private val inputJars: ConfigurableFileCollection,
  private val inputChanges: InputChanges,
) {

  fun compute(): JarChanges {
    val (changed, addedOrRemoved) = inputChanges
      .getFileChanges(inputJars)
      .partition { it.changeType == ChangeType.MODIFIED }

    val reprocessAll = !inputChanges.isIncremental || addedOrRemoved.isNotEmpty()
    val changedFiles = changed.map { it.file }.toSet()
    val hasChanged = { file: File -> reprocessAll || (file in changedFiles) }
    val jarsInfo = inputJars.files.mapIndexedNotNull { index: Int, file: File ->
      if (!hasChanged(file)) null
      else FileInfo(
        identity = index.toString(),
        file = file,
      )
    }

    return JarChanges(jarsInfo, reprocessAll)
  }

  data class JarChanges(
    val jarsInfo: List<FileInfo>,
    val reprocessAll: Boolean,
  )

  data class FileInfo(
    val identity: String,
    val file: File,
  )
}