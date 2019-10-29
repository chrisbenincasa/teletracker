import {
  Button,
  createStyles,
  Grid,
  Theme,
  Typography,
  WithStyles,
  withStyles,
  withWidth,
} from '@material-ui/core';
import { ExitToApp, PersonAdd } from '@material-ui/icons';
import * as R from 'ramda';
import React, { Component } from 'react';
import ReactGA from 'react-ga';
import { connect } from 'react-redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import withUser, { WithUserProps } from '../components/withUser';
import { GA_TRACKING_ID } from '../constants/';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { Item } from '../types/v2/Item';
import Odometer from 'react-odometerjs';
import 'odometer/themes/odometer-theme-default.css';
import AuthDialog from '../components/Auth/AuthDialog';
import { retrievePopular } from '../actions/popular';
import { PopularInitiatedActionPayload } from '../actions/popular/popular';
import ItemCard from '../components/ItemCard';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    buttonContainer: {
      display: 'flex',
      flexGrow: 1,
      flexDirection: 'row',
    },
    buttonIcon: {
      marginRight: 8,
    },
    container: {
      display: 'flex',
      flexDirection: 'row',
      margin: 20,
      alignItems: 'center',
      justifyContent: 'center',
      [theme.breakpoints.down('sm')]: {
        flexDirection: 'column-reverse',
        margin: 5,
      },
    },
    ctaContainer: {
      display: 'flex',
      flexDirection: 'column',
      margin: 100,
      [theme.breakpoints.down('sm')]: {
        margin: 0,
        alignItems: 'center',
        textAlign: 'center',
      },
    },
    gridContainer: {
      width: '20%',
      [theme.breakpoints.down('sm')]: {
        width: '100%',
      },
    },
    loginButton: {
      margin: `${theme.spacing(2)}px ${theme.spacing(2)}px ${theme.spacing(
        2,
      )}px ${theme.spacing(1)}px`,
      width: '100%',
      maxWidth: 200,
      whiteSpace: 'nowrap',
    },
    movieCount: {
      marginRight: 15,
      whiteSpace: 'nowrap',
    },
    movieCountContainer: {
      display: 'flex',
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'flex-start',
      [theme.breakpoints.down('sm')]: {
        flexDirection: 'column',
        alignItems: 'flex-center',
      },
    },
    signupButton: {
      margin: `${theme.spacing(2)}px ${theme.spacing(1)}px ${theme.spacing(
        2,
      )}px 0`,
      width: '100%',
      maxWidth: 200,
      whiteSpace: 'nowrap',
    },
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps extends WithStyles<typeof styles> {
  isAuthed: boolean;
  loading: boolean;
  thingsBySlug: { [key: string]: Item };
  popular?: string[];
}

interface WidthProps {
  width: string;
}

interface RouteParams {
  id: string;
  type: string;
}

interface DispatchProps {
  retrievePopular: (payload: PopularInitiatedActionPayload) => void;
}

type Props = OwnProps &
  InjectedProps &
  WidthProps &
  WithUserProps &
  RouteComponentProps<RouteParams> &
  DispatchProps;

interface State {
  numberMovies: number;
  authModalOpen: boolean;
  authModalScreen?: 'login' | 'signup';
}

class Home extends Component<Props, State> {
  constructor(props) {
    super(props);

    this.state = {
      numberMovies: 0,
      authModalOpen: false,
      authModalScreen: 'login',
    };
  }

  loadPopular() {
    const { retrievePopular } = this.props;

    // To do: add support for sorting
    if (!this.props.loading) {
      retrievePopular({
        itemTypes: ['movie'],
        limit: 4,
      });
    }
  }

  componentDidMount() {
    const { isLoggedIn, userSelf } = this.props;
    this.loadPopular();

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);

    if (isLoggedIn && userSelf && userSelf.user && userSelf.user.uid) {
      ReactGA.set({ userId: userSelf.user.uid });
    }

    this.setState({ numberMovies: 488689 });
  }

  toggleAuthModal = (initialForm?: 'login' | 'signup') => {
    if (['xs', 'sm', 'md'].includes(this.props.width)) {
      this.setState({
        authModalOpen: false,
        authModalScreen: undefined,
      });
      this.props.history.push(`/${initialForm}`);
    } else {
      this.setState({
        authModalOpen: !this.state.authModalOpen,
        authModalScreen: initialForm,
      });
    }
  };

  render() {
    const { classes, popular, userSelf, thingsBySlug } = this.props;

    return !this.props.isAuthed ? (
      <React.Fragment>
        <div className={classes.container}>
          <div className={classes.gridContainer}>
            <Grid container spacing={1}>
              {popular &&
                popular.map(result => {
                  let thing = thingsBySlug[result];
                  return (
                    <ItemCard
                      key={result}
                      userSelf={userSelf}
                      item={thing}
                      gridProps={{ xs: 6, sm: 6, md: 6, lg: 6 }}
                      hoverAddToList={false}
                      hoverDelete={false}
                      hoverWatch={false}
                    />
                  );
                })}
            </Grid>
          </div>
          <div className={classes.ctaContainer}>
            <div className={classes.movieCountContainer}>
              <Typography
                color="secondary"
                variant="h2"
                className={classes.movieCount}
              >
                <Odometer
                  value={this.state.numberMovies}
                  format="(,ddd)"
                  theme="default"
                  duration={5000}
                  animation="count"
                />
              </Typography>
              <Typography variant="h2"> movies &amp; counting.</Typography>
            </div>
            <Typography variant="h4" color="textSecondary">
              Discover what you're not watching.
            </Typography>
            <div className={classes.buttonContainer}>
              <Button
                size="small"
                variant="outlined"
                aria-label="Signup"
                onClick={() => this.toggleAuthModal('signup')}
                color="secondary"
                className={classes.signupButton}
              >
                <PersonAdd className={classes.buttonIcon} />
                Signup
              </Button>
              <Button
                size="small"
                variant="outlined"
                aria-label="Login"
                onClick={() => this.toggleAuthModal('login')}
                className={classes.loginButton}
              >
                <ExitToApp className={classes.buttonIcon} />
                Login
              </Button>
            </div>
          </div>
        </div>
        <AuthDialog
          open={this.state.authModalOpen}
          onClose={() => this.toggleAuthModal()}
          initialForm={this.state.authModalScreen}
        />
      </React.Fragment>
    ) : (
      <Redirect to="/" />
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    loading: appState.popular.loadingPopular,
    popular: appState.popular.popular,
    thingsBySlug: appState.itemDetail.thingsBySlug,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      retrievePopular,
    },
    dispatch,
  );

export default withWidth()(
  withUser(
    withStyles(styles, { withTheme: true })(
      withRouter(
        connect(
          mapStateToProps,
          mapDispatchToProps,
        )(Home),
      ),
    ),
    () => null,
  ),
);
