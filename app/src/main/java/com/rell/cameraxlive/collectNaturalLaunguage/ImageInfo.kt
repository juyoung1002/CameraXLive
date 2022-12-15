package com.rell.cameraxlive.collectNaturalLaunguage

data class ImageInfo(
    val name: String,
    val imageUrl: String
) {
    companion object {
        fun fake(): List<ImageInfo> {
            val list = ArrayList<ImageInfo>()
//            ImageInfo(
//                name = "더 심플 데일리 로션",
//                imageUrl = "https://dev.img.hwahae.co.kr/products/1803575/1803575_20220801000000.jpg"
//            ).also(list::add)
//            ImageInfo(
//                name = "너리싱 샴푸",
//                imageUrl = "https://dev.img.hwahae.co.kr/products/1836575/1836575_20220801000000.jpg"
//            ).also(list::add)
//            ImageInfo(
//                name = "아이깨끗해",
//                imageUrl = "https://dev.img.hwahae.co.kr/products/1940840/1940840_20220801000000.jpg"
//            ).also(list::add)
//            ImageInfo(
//                name = "프롬맘 젠틀 로션 포 베이비",
//                imageUrl = "https://dev.img.hwahae.co.kr/products/1996191/1996191_20220801000000.jpg"
//            ).also(list::add)
            ImageInfo(
                name = "그린핑거 베이비&임산부 클렌저/워시",
                imageUrl = "https://dev.img.hwahae.co.kr/products/10151/10151_20220801000000.jpg"
            ).also(list::add)
            return list
        }
    }
}
