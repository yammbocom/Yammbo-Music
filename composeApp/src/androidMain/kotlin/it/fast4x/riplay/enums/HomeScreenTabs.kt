package it.fast4x.riplay.enums

enum class HomeScreenTabs {
    Default,
    Inicio,
    LiveRadio,
    MyMusic,
    Search,
    MyAccount;

    val index: Int
        get() = when (this) {
            Default -> 100
            Inicio -> 0
            LiveRadio -> 1
            MyMusic -> 2
            Search -> 3
            MyAccount -> 4
        }

}
