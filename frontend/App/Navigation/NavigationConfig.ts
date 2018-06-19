export const AuthStack = {
    root: {
        stack: {
            id: 'Login',
            children: [{
                component: {
                    name: 'navigation.main.LoginScreen',
                    options: {
                        topBar: {
                            visible: false
                        }
                    }
                }
            }]
        }
    }
};

export let ListView = {
    bottomTabs: {
        children: [{
            stack: {
                children: [{
                    component: {
                        name: 'navigation.main.ListView',
                            passProps: {
                                text: 'List View'
                            },
                            options: {
                                bottomTab: {
                                    title: 'My List',
                                    icon: require('../Images/Icons/list-icon.png'),
                                    testID: 'FIRST_TAB_BAR_BUTTON'  
                                },
                                topBar: {
                                    visible: false
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
                        name: 'navigation.main.SearchScreen',
                        passProps: {
                            text: 'Search'
                        },
                        options: {
                            bottomTab: {
                                title: 'Search',
                                icon: require('../Images/Icons/search.png'),
                                testID: 'SECOND_TAB_BAR_BUTTON'
                            },
                            topBar: {
                                visible: false,
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
                            title: 'Notifications',
                            icon: require('../Images/Icons/notification.png'),
                            testID: 'THIRD_TAB_BAR_BUTTON'
                        },
                        topBar: {
                            visible: false
                        }
                    }
                }
            }]
        }
    }]
  }
}

export let MenuView = {
    component: {
        name: 'navigation.main.MenuScreen',
        options: {
            animated: true,
            topBar: {
                visible: false
            }
        }
    }
}

export let DetailView = {
    component: {
        name: 'navigation.main.ItemDetailScreen',
        options: {
            animated: true,
            topBar: {
                visible: false
            },
        }
    }
}

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
            },
        }
    }
}

// Initial State of the App stack
export const AppStack = {
    root: ListView,
    options: {
        topBar: {
            visible: false
        }
    }
};