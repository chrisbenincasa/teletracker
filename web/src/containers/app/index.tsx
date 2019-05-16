import {
  AppBar,
  Button,
  createStyles,
  IconButton,
  InputBase,
  Menu,
  MenuItem,
  Theme,
  Toolbar,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { fade } from '@material-ui/core/styles/colorManipulator';
import {
  AccountCircleOutlined,
  HomeOutlined,
  List,
  Tv,
} from '@material-ui/icons';
import SearchIcon from '@material-ui/icons/Search';
import _ from 'lodash';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Link, Route, RouteComponentProps, withRouter } from 'react-router-dom';
import { bindActionCreators, Dispatch } from 'redux';
import { checkAuth, logout } from '../../actions/auth';
import { search } from '../../actions/search';
import { AppState } from '../../reducers';
import About from '../about';
import Account from '../account';
import Home from '../home';
import ListDetail from '../list-detail';
import ItemDetail from '../item-detail';
import Lists from '../lists';
import Login from '../login';

const styles = (theme: Theme) =>
  createStyles({
    root: {
      flexGrow: 1,
    },
    grow: {
      flexGrow: 1,
    },
    menuButton: {
      marginLeft: -12,
      marginRight: 20,
    },
    search: {
      position: 'relative',
      borderRadius: theme.shape.borderRadius,
      backgroundColor: fade(theme.palette.common.white, 0.15),
      '&:hover': {
        backgroundColor: fade(theme.palette.common.white, 0.25),
      },
      marginRight: theme.spacing.unit * 2,
      marginLeft: 0,
      width: '100%',
      [theme.breakpoints.up('sm')]: {
        marginLeft: theme.spacing.unit * 3,
        width: 'auto',
      },
      [theme.breakpoints.down('sm')]: {
        display: 'none',
      },
    },
    searchMobile: {
      position: 'relative',
      borderRadius: theme.shape.borderRadius,
      backgroundColor: fade(theme.palette.common.white, 0.15),
      '&:hover': {
        backgroundColor: fade(theme.palette.common.white, 0.25),
      },
      marginRight: theme.spacing.unit * 2,
      marginLeft: 0,
      width: '100%',
    },
    searchIcon: {
      width: theme.spacing.unit * 9,
      height: '100%',
      position: 'absolute',
      pointerEvents: 'none',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
    },
    inputRoot: {
      color: 'inherit',
      width: '100%',
    },
    inputInput: {
      paddingTop: theme.spacing.unit,
      paddingRight: theme.spacing.unit,
      paddingBottom: theme.spacing.unit,
      paddingLeft: theme.spacing.unit * 10,
      transition: theme.transitions.create('width'),
      width: '100%',
      [theme.breakpoints.up('md')]: {
        width: 400,
      },
    },
    sectionDesktop: {
      display: 'none',
      [theme.breakpoints.up('md')]: {
        display: 'flex',
      },
    },
    sectionMobile: {
      display: 'flex',
      flex: '1 1 auto',
      justifyContent: 'flex-end',
      [theme.breakpoints.up('md')]: {
        display: 'none',
      },
    },
    appBar: {
      zIndex: 99999,
    }
  });

interface OwnProps extends WithStyles<typeof styles> {
  isAuthed: boolean;
  currentSearchText?: string;
}

interface DispatchProps {
  checkAuth: () => void;
  logout: () => void;
  search: (text: string) => void;
}

type Props = DispatchProps & OwnProps & RouteComponentProps;

interface State {
  anchorEl: any;
  searchText: string;
  mobileSearchBarOpen: boolean;
}

class App extends Component<Props, State> {
  state = {
    anchorEl: null,
    searchText: '',
    mobileSearchBarOpen: false,
  };

  handleSearchChange = event => {
    let searchText = event.currentTarget.value;

    this.setState({ searchText });

    this.debouncedExecSearch(searchText);
  };

  handleSearchForEnter = (ev: React.KeyboardEvent<HTMLInputElement>) => {
    if (ev.keyCode === 13) {
      this.execSearch(ev.currentTarget.value, true);
    }
  };

  execSearch = (text: string, force: boolean = false) => {
    if (this.props.location.pathname !== '/') {
      this.props.history.push('/');
    }

    if (text.length >= 1 && (force || this.props.currentSearchText !== text)) {
      this.props.search(text);
    }
  };

  debouncedExecSearch = _.debounce(this.execSearch, 250);

  handleMobileSearchDisplay = () => {
    this.setState({ mobileSearchBarOpen: !this.state.mobileSearchBarOpen });
  };

  handleMenu = event => {
    this.setState({ anchorEl: event.currentTarget });
  };

  handleClose = () => {
    this.setState({ anchorEl: null });
  };

  handleLogout = () => {
    this.handleClose();
    this.props.logout();
  };

  renderSearch() {
    if (!this.props.isAuthed) {
      return null;
    }

    let { classes } = this.props;
    let { mobileSearchBarOpen } = this.state;

    return (
      <React.Fragment>
        <div className={classes.sectionDesktop}>
          <div className={classes.search}>
            <div className={classes.searchIcon}>
              <SearchIcon />
            </div>
            <InputBase
              placeholder="Search…"
              classes={{
                root: classes.inputRoot,
                input: classes.inputInput,
              }}
              onChange={this.handleSearchChange}
              onKeyDown={this.handleSearchForEnter}
            />
          </div>
          <div className={classes.grow} />
        </div>
        <div className={classes.sectionMobile}>
          <IconButton
            aria-owns={mobileSearchBarOpen ? 'material-appbar' : undefined}
            aria-haspopup="true"
            onClick={this.handleMobileSearchDisplay}
            color="inherit"
            style={mobileSearchBarOpen ? {backgroundColor: 'rgba(250,250,250, 0.15)'} : undefined}
          >
            <SearchIcon />
          </IconButton>
        </div>
      </React.Fragment>
    );
  }

  renderMobileSearchBar() {
    let { classes } = this.props;

    return (
      <React.Fragment>
        <div className={classes.sectionMobile}>
          <div className={classes.searchMobile}>
            <div className={classes.searchIcon}>
              <SearchIcon />
            </div>
            <InputBase
              placeholder="Search…"
              classes={{
                root: classes.inputRoot,
                input: classes.inputInput,
              }}
              onChange={this.handleSearchChange}
              onKeyDown={this.handleSearchForEnter}
            />
          </div>
        </div>
      </React.Fragment>
    );
  }

  renderProfileMenu() {
    if (!this.props.isAuthed) {
      return null;
    }

    let { anchorEl } = this.state;
    let { classes } = this.props;
    let isMenuOpen = !!anchorEl;

    return (
      <div className={classes.sectionDesktop}>
        <IconButton
          aria-owns={isMenuOpen ? 'material-appbar' : undefined}
          aria-haspopup="true"
          onClick={this.handleMenu}
          color="inherit"
        >
          <AccountCircleOutlined />
        </IconButton>
        <Menu
          anchorEl={anchorEl}
          anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
          transformOrigin={{ vertical: 'top', horizontal: 'right' }}
          open={!!this.state.anchorEl}
          onClose={this.handleClose}
          disableAutoFocusItem
        >
          <MenuItem onClick={this.handleClose}>Profile</MenuItem>
          <MenuItem
            component={props => <Link {...props} to="/account" />}
            onClick={this.handleClose}
          >
            My account
          </MenuItem>
          <MenuItem onClick={this.handleLogout}>Logout</MenuItem>
        </Menu>
      </div>
    );
  }

  render() {
    let { anchorEl } = this.state;
    let { classes } = this.props;

    return (
      <div className={classes.root}>
        <AppBar position="sticky" className={classes.appBar}>
          <Toolbar>
            <IconButton
                component={props => <Link {...props} to="/" />}
                color="inherit"
              >
                <Tv />
            </IconButton>
            <Typography variant="h6" color="inherit" className={classes.grow}>
              Teletracker
            </Typography>
            {this.renderSearch()}
            {!this.props.isAuthed ? (
              <Button
                component={props => <Link {...props} to="/login" />}
                color="inherit"
              >
                Login
              </Button>
            ) : null}
            {this.props.isAuthed ? (
              <IconButton
                component={props => <Link {...props} to="/lists" />}
                color="inherit"
              >
                <List />
              </IconButton>
            ) : null}
            {this.renderProfileMenu()}
          </Toolbar>
          {this.state.mobileSearchBarOpen ? (
            <Toolbar>
              {this.renderMobileSearchBar()}
            </Toolbar>
          ) : null }
        </AppBar>
        <div>
          <main>
            <Route exact path="/" component={Home} />
            <Route exact path="/account" component={Account} />
            <Route exact path="/login" component={Login} />
            <Route exact path="/lists" component={Lists} />
            <Route exact path="/lists/:id" component={ListDetail} />
            <Route exact path="/item/:type/:id" component={ItemDetail} />
            <Route exact path="/logout" component={About} />
          </main>
        </div>
      </div>
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    currentSearchText: R.path<string>(
      ['search', 'currentSearchText'],
      appState,
    ),
  };
};

const mapDispatchToProps: (dispatch: Dispatch) => DispatchProps = dispatch => {
  return bindActionCreators(
    {
      checkAuth,
      logout,
      search,
    },
    dispatch,
  );
};

export default withRouter(
  withStyles(styles)(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(App),
  ),
);
