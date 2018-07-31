import Immutable from 'seamless-immutable';
import Colors from '../Themes/Colors';
import { Navigation } from 'react-native-navigation';
import { ListDetailNavOptions } from '../Containers/ListDetailScreen';

export const LoginScreenComponent = {
    component: {
        name: 'navigation.main.LoginScreen',
        options: {
            topBar: {
                visible: false
            }
        }
    }
};


export const AuthStack = {
    root: {
        stack: {
            id: 'Login',
            children: [LoginScreenComponent]
        }
    }
};

export const AuthStack2 = {
    root: {
        sideMenu: {
            left: {
                component: {
                    name: 'navigation.main.MenuScreen',
                    passProps: {
                        side: 'left'
                    }
                },
                options: {
                    enabled: false
                },
                enabled: false
            },
            center: {
                stack: {
                    children: [
                        LoginScreenComponent
                    ]
                }
            },
            options: {
                topBar: {
                    visible: false
                },
                statusBar: {
                    style: 'dark'
                },
                sideMenu: {
                    left: {
                        enabled: false
                    }
                }
            }
        }
    }
};

export let ListBottomTabs = {
    bottomTabs: {
        id: 'BottomTabs',
        children: [{
            stack: {
                children: [{
                    component: {
                        name: 'navigation.main.ListView',
                        passProps: {
                            text: 'List View'
                        },
                        options: ListDetailNavOptions
                    }
                }]
            }
        },
        {
            stack: {
                children: [{
                    component: {
                        name: 'navigation.main.SearchScreen',
                        passProps: {
                            text: 'Search'
                        },
                        options: {
                            bottomTab: {
                                text: 'Search',
                                icon: require('../Images/Icons/search.png'),
                                testID: 'SECOND_TAB_BAR_BUTTON'
                            },
                            topBar: {
                                title: {
                                    text: 'Search'
                                },
                                // searchBar: true,
                                background: {
                                    color: Colors.headerBackground
                                },
                                visible: true
                            }
                        }
                    }
                }]
            }
        },
        {
            stack: {
                children: [{
                    component: {
                        name: 'navigation.main.NotificationsScreen',
                        passProps: {
                            text: 'Notifications'
                        },
                        options: {
                            bottomTab: {
                                text: 'Notifications',
                                icon: require('../Images/Icons/notification.png'),
                                testID: 'THIRD_TAB_BAR_BUTTON'
                            },
                            topBar: {
                                title: {
                                    text: 'Notifications'
                                },
                                visible: true
                            }
                        }
                    }
                }]
            }
        }],
        options: {
            sideMenu: {
                left: {
                    enabled: true
                }
            }
        }
    }
}

export let ListView = {
    sideMenu: {
        left: {
          component: {
            name: 'navigation.main.MenuScreen',
            passProps: {
              side: 'left'
            }
          }
        },
        center: {
            ...ListBottomTabs,
            options: {
                showsShadow: false
            }
        },
        options: {
            sideMenu: {
                left: {
                    enabled: true
                }
            }
        }
    },
    options: {
        statusBar: {
            style: 'light'
        }
    }
}

export let MenuView = {
    component: {
        name: 'navigation.main.MenuScreen',
        options: {
            animated: true
        }
    }
}

export let DetailView = Immutable({
    component: {
        name: 'navigation.main.ItemDetailScreen',
        options: {
            animated: true,
            topBar: {
                visible: false
            }
        }
    }
})

export let SearchView = {
    component: {
        name: 'navigation.main.SearchScreen',
        options: {
            animated: true,
            topBar: {
                visible: false
            },
        }
    }
}

export let NotificationsView = {
    component: {
        name: 'navigation.main.NotificationsScreen',
        options: {
            animated: true,
            topBar: {
                visible: false
            }
        }
    }
}

// Initial State of the App stack
export const AppStack = {
    // root: {
    //     ...ListBottomTabs,
    //     options: {
    //         showsShadow: false
    //     }
    // },
    root: ListView,
    options: {
        topBar: {
            visible: true
        },
        statusBar: {
            style: 'light'
        }
    }
};

export const AppStack2 = {
    children: [
        LoginScreenComponent
    ]
}

export const NavigationConfig = {
    LoginScreenComponent,
    AuthStack,
    AuthStack2,
    ListView,
    ListBottomTabs,
    MenuView,
    DetailView,
    SearchView,
    NotificationsView,
    AppStack,
    AppStack2
}