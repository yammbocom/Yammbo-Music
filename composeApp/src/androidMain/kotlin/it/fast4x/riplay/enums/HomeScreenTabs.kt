package it.fast4x.riplay.enums

enum class HomeScreenTabs {
    Default,
    Inicio,
    Top50,
    MyMusic,
    Search,
    MyAccount;

    val index: Int
        get() = when (this) {
            Default -> 100
            Inicio -> 0
            Top50 -> 1
            MyMusic -> 2
            Search -> 3
            MyAccount -> 4
        }

}