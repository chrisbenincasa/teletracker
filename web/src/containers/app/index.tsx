import {
  AppBar,
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
import Home from '../home';
import Login from '../login';
import Lists from '../lists';
import ListDetail from '../list-detail';

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
}

class App extends Component<Props, State> {
  state = {
    anchorEl: null,
    searchText: '',
  };

  componentDidMount() {
    this.props.checkAuth();
  }

  handleSearchChange = event => {
    let searchText = event.currentTarget.value;

    this.setState({ searchText });

    this.execSearch(searchText);
  };

  execSearch = _.debounce((text: string) => {
    if (this.props.location.pathname !== '/') {
      this.props.history.push('/');
    }

    if (text.length >= 1 && this.props.currentSearchText !== text) {
      this.props.search(text);
    }
  }, 250);

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

    return (
      <React.Fragment>
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
          />
        </div>
        <div className={classes.grow} />
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
        >
          <MenuItem onClick={this.handleClose}>Profile</MenuItem>
          <MenuItem onClick={this.handleClose}>My account</MenuItem>
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
        <AppBar position="static">
          <Toolbar>
            <Tv style={{ marginRight: '10px' }} />
            <Typography variant="h6" color="inherit" className={classes.grow}>
              Teletracker
            </Typography>
            {this.renderSearch()}
            <IconButton
              component={props => <Link {...props} to="/" />}
              color="inherit"
            >
              <HomeOutlined />
            </IconButton>
            {!this.props.isAuthed ? (
              <IconButton
                component={props => <Link {...props} to="/login" />}
                color="inherit"
              >
                Login
              </IconButton>
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
        </AppBar>
        <div>
          <main>
            <Route exact path="/" component={Home} />
            <Route exact path="/about-us" component={About} />
            <Route exact path="/login" component={Login} />
            <Route exact path="/lists" component={Lists} />
            <Route exact path="/lists/:id" component={ListDetail} />
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
