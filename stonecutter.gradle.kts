plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11"

stonecutter parameters {
    dependencies["fapi"] = node.project.property("deps.fabric_api") as String
}
