package com.example.landscapedesign.report

import com.example.landscapedesign.geometry.GeometryUtils
import com.example.landscapedesign.model.BorderElement
import com.example.landscapedesign.model.BorderTier
import com.example.landscapedesign.model.DesignLayoutState
import com.example.landscapedesign.model.PlantNode
import com.example.landscapedesign.model.PlantType
import com.example.landscapedesign.model.Point3D
import com.example.landscapedesign.model.ShapeElement

/**
 * Compiles the entire [DesignLayoutState] into a single, detailed Arabic
 * narrative report intended to feed a downstream generative-AI rendering
 * stage. All four sections are populated live from current app state.
 */
object ReportGenerator {

    /**
     * @param boundary the base garden polygon (used to compute each major
     * tree's distance to the two nearest boundary edges).
     */
    fun generateFinalLandscapeReport(
        state: DesignLayoutState,
        boundary: List<Point3D> = state.gardenBoundary
    ): String {
        val sb = StringBuilder()

        sb.append(sectionOneGeneralDimensions(state))
        sb.append("\n\n")
        sb.append(sectionTwoMajorTrees(state, boundary))
        sb.append("\n\n")
        sb.append(sectionThreeBorders(state))
        sb.append("\n\n")
        sb.append(sectionFourLawn(state))

        return sb.toString()
    }

    // -----------------------------------------------------------------
    // SECTION 1: General dimensions & site preparation
    // -----------------------------------------------------------------
    private fun sectionOneGeneralDimensions(state: DesignLayoutState): String {
        return buildString {
            append("القسم الأول: الأبعاد العامة وتجهيز الموقع\n")
            append(
                "المساحة الإجمالية للحديقة هي %.2f متر مربع. ".format(state.gardenAreaM2)
            )
            append(
                "سماكة الردم بالتراب المطلوبة هي %d سم، مما ينتج عنه حجم إجمالي من التراب المطلوب يبلغ %.2f متر مكعب."
                    .format(state.soilThicknessCm, state.soilVolumeM3)
            )
        }
    }

    // -----------------------------------------------------------------
    // SECTION 2: Major trees & spatial positioning
    // -----------------------------------------------------------------
    private fun sectionTwoMajorTrees(state: DesignLayoutState, boundary: List<Point3D>): String {
        val majorTrees = state.plants.filter {
            it.type == PlantType.ROYAL_PALM || it.type == PlantType.NO_THORN
        }
        if (majorTrees.isEmpty()) {
            return "القسم الثاني: الأشجار الرئيسية وموقعها المكاني\nلم تتم إضافة أشجار رئيسية بعد."
        }

        val sb = StringBuilder("القسم الثاني: الأشجار الرئيسية وموقعها المكاني\n")
        for (tree in majorTrees) {
            val treeName = plantDisplayName(tree)
            val edges = if (boundary.size >= 2) {
                GeometryUtils.nearestTwoEdgeDistances(tree.world, boundary)
            } else emptyList()

            val distRight = edges.getOrNull(0)?.distanceMeters ?: 0f
            val distLeft = edges.getOrNull(1)?.distanceMeters ?: 0f

            sb.append(
                "شجرة %s رئيسية تقع على بعد %.2f متر من الحد الأيمن للحديقة و%.2f متر من الحد الأيسر. "
                    .format(treeName, distRight, distLeft)
            )

            val attachedShape = state.shapes.firstOrNull { it.attachedTreeId == tree.id }
            if (attachedShape != null) {
                val shapeLabel = shapeDisplayName(attachedShape)
                val radius = attachedShape.radiusMeters ?: 0f
                sb.append(
                    "تحيط بهذه الشجرة عنصر تنسيقي على شكل %s بنصف قطر دقيق يبلغ %.2f متر. "
                        .format(shapeLabel, radius)
                )
                if (attachedShape.hedgePlantName != null) {
                    sb.append(
                        "تمت زراعة هذا المحيط كتحوّط باستخدام نبات %s بكثافة زراعة %.1f شجرة في المتر، بإجمالي %d نبتة تحوّط مطلوبة."
                            .format(
                                attachedShape.hedgePlantName,
                                attachedShape.hedgeDensityPerMeter ?: 0f,
                                attachedShape.hedgePlantCount ?: 0
                            )
                    )
                }
            }
            sb.append("\n")
        }
        return sb.toString().trim()
    }

    // -----------------------------------------------------------------
    // SECTION 3: Landscape borders & eraser integration
    // -----------------------------------------------------------------
    private fun sectionThreeBorders(state: DesignLayoutState): String {
        val sb = StringBuilder("القسم الثالث: محددات الحديقة والفتحات (الأسوار والممرات)\n")
        for (border in state.borders) {
            sb.append(borderDescription(border))
            sb.append("\n")
        }
        return sb.toString().trim()
    }

    private fun borderDescription(border: BorderElement): String {
        val tierLabel = when (border.tier) {
            BorderTier.LARGE -> "كبير"
            BorderTier.MEDIUM -> "متوسط"
            BorderTier.SMALL -> "صغير"
        }
        val netLength = GeometryUtils.round2(border.netLengthMeters())
        if (border.rawLengthMeters() <= 0f) {
            return "المحدد ال$tierLabel بسماكة ${border.tier.thicknessCm} سم لم يتم رسمه بعد."
        }

        val statusText = if (border.isStructural) {
            "إنشائي (خرساني/حجري) بدون نباتات"
        } else {
            "مزروع بنبات ${border.plantName}"
        }

        val plantCountText = if (!border.isStructural) {
            " عدد النباتات المطلوبة لهذا المحدد هو ${border.requiredPlantCount()} نبتة بناءً على كثافة ${border.densityPerMeter ?: 0f} نبتة في المتر."
        } else ""

        return "محدد $tierLabel بسماكة ${border.tier.thicknessCm} سم يمتد بطول صافٍ يبلغ %.2f متر. تم تكوينه كـ%s. يحتوي هذا المحدد على %d فتحة/باب تم إنشاؤها بواسطة المستخدم، مما قلل طوله الأصلي بمقدار %.2f متر.%s"
            .format(netLength, statusText, border.openingsCount, GeometryUtils.round2(border.eraserGapsMeters), plantCountText)
    }

    // -----------------------------------------------------------------
    // SECTION 4: Net lawn area & seeding density
    // -----------------------------------------------------------------
    private fun sectionFourLawn(state: DesignLayoutState): String {
        return "القسم الرابع: مساحة النجيلة الصافية وكثافة البذر\n" +
            "بعد خصم جميع المحددات الإنشائية ومناطق الأشجار ومحيطاتها بدقة، تم حساب المساحة المتبقية المفتوحة. " +
            "مساحة النجيلة الصافية المخصصة لزراعة العشب هي بالضبط %.2f متر مربع. ".format(state.netLawnAreaM2) +
            "سيتم زراعة هذه المساحة بشتلات النجيل بكثافة %d شتلة في المتر المربع، بإجمالي مطلوب يبلغ %d شتلة نجيل."
                .format(state.lawnDensityPerM2, state.totalLawnPlants)
    }

    private fun plantDisplayName(plant: PlantNode): String = when (plant.type) {
        PlantType.NO_THORN -> "اللاشوكة"
        PlantType.ROYAL_PALM -> "رويال بالم"
        PlantType.DURANTA -> "دورنتا"
        PlantType.CUSTOM -> plant.customName ?: "نبات مخصص"
    }

    private fun shapeDisplayName(shape: ShapeElement): String = when (shape.type) {
        com.example.landscapedesign.model.ShapeType.ARC -> "قوس"
        com.example.landscapedesign.model.ShapeType.CIRCLE -> "دائرة"
        com.example.landscapedesign.model.ShapeType.POLYGON -> "شكل مخصص"
    }
}
