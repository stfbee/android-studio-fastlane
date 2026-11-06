package dev.fastlane.ide

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor

class LaneFindUsagesFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement) = element is LaneNavigationElement
    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? =
        (element as? LaneNavigationElement)?.let { LaneFindUsagesHandler(it) }
}

class LaneFindUsagesHandler(private val lane: LaneNavigationElement) : FindUsagesHandler(lane) {
    override fun getFindUsagesOptions(dataContext: DataContext?): FindUsagesOptions =
        super.getFindUsagesOptions(dataContext).apply {
            searchScope = LocalSearchScope(lane.containingFile, "Current Fastfile")
            isSearchForTextOccurrences = false
        }

    override fun isSearchForTextOccurrencesAvailable(element: PsiElement, isSingleFile: Boolean) = false

    override fun processElementUsages(element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions): Boolean =
        ReadAction.compute<Boolean, RuntimeException> {
        val target = element as? LaneNavigationElement ?: return@compute true
        if (!target.isValid) return@compute true
        val file = target.containingFile
        if (!options.isUsages || !PsiSearchScopeUtil.isInScope(options.searchScope, file)) return@compute true
        for (call in FastlaneAnalysis.calls(file)) {
            ProgressManager.checkCanceled()
            if (call.targets.any { it.nameOffset == target.textOffset } &&
                !processor.process(UsageInfo(file, call.range.startOffset, call.range.endOffset, false))) return@compute false
        }
        true
    }
}

class LaneFindUsagesProvider : FindUsagesProvider {
    override fun canFindUsagesFor(element: PsiElement) = element is LaneNavigationElement
    override fun getHelpId(element: PsiElement): String? = null
    override fun getType(element: PsiElement) = if (element is LaneNavigationElement) "lane" else ""
    override fun getDescriptiveName(element: PsiElement) = (element as? LaneNavigationElement)?.name ?: element.text
    override fun getNodeText(element: PsiElement, useFullName: Boolean) = getDescriptiveName(element)
}
