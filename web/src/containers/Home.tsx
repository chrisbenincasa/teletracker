import {
  Button,
  Divider,
  Grid,
  makeStyles,
  Theme,
  Typography,
  withWidth,
} from '@material-ui/core';
import { ExitToApp, List, PersonAdd } from '@material-ui/icons';
import * as R from 'ramda';
import React, { useEffect, useState } from 'react';
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
import Typist from 'react-typist';
import 'react-typist/dist/Typist.css';
import 'odometer/themes/odometer-theme-default.css';
import AuthDialog from '../components/Auth/AuthDialog';
import { retrievePopular } from '../actions/popular';
import { PopularInitiatedActionPayload } from '../actions/popular/popular';
import ItemCard from '../components/ItemCard';
import _ from 'lodash';

const useStyles = makeStyles((theme: Theme) => ({
  layout: layoutStyles(theme),
  buttonContainer: {
    display: 'flex',
    flexDirection: 'row',
  },
  buttonIcon: {
    marginRight: 8,
  },
  container: {
    display: 'flex',
    flexDirection: 'row',
    margin: `${theme.spacing(3)}px`,
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
  listCtaContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'absolute',
    zIndex: 99,
    top: 0,
    width: '100%',
    height: '100%',
    backgroundColor: 'rgba(48, 48, 48, 0.5)',
    backgroundImage:
      'linear-gradient(to top, rgba(255, 255, 255,0) 0%,rgba(48, 48, 48,1) 100%)',
  },
  listContainer: {
    display: 'flex',
    flexDirection: 'column',
    position: 'relative',
    margin: `${theme.spacing(4)}px 0px`,
  },
  listTitleContainer: {
    display: 'flex',
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'center',
    zIndex: 999,
    width: '100%',
    margin: `${theme.spacing(2)}px 0px`,
  },
  loginButton: {
    margin: `${theme.spacing(2)}px ${theme.spacing(2)}px ${theme.spacing(
      2,
    )}px ${theme.spacing(1)}px`,
    width: '100%',
    maxWidth: 200,
    whiteSpace: 'nowrap',
  },
  highlightText: {
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
  posterContainer: {
    display: 'flex',
    flexDirection: 'row',
    flexWrap: 'nowrap',
    margin: `${theme.spacing(3)}px`,
    filter: 'blur(3px)',
  },
  searchContainer: {
    display: 'flex',
    flexGrow: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    margin: `${theme.spacing(3)}px`,
    [theme.breakpoints.down('sm')]: {
      flexDirection: 'column-reverse',
      margin: 5,
    },
  },
  searchCtaContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    margin: 100,
    [theme.breakpoints.down('sm')]: {
      margin: 0,
      textAlign: 'center',
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
  wrapper: {
    display: 'flex',
    flexDirection: 'column',
  },
}));

interface InjectedProps {
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

type Props = InjectedProps &
  WidthProps &
  WithUserProps &
  RouteComponentProps<RouteParams> &
  DispatchProps;

function Home(props: Props) {
  const classes = useStyles();
  const defaultStrings: string[] = [
    "90's movies starring Keanu Reeves",
    "Movies i've liked",
    'Comedy tv shows on Netflix',
    'The Matrix',
    'Movies like The Matrix',
    'Show with Michael Scott',
  ];
  const [numberMovies, setNumberMovies] = useState<number>(0);
  const [authModalOpen, setAuthModalOpen] = useState<boolean>(false);
  const [authModalScreen, setAuthModalScreen] = useState<
    'login' | 'signup' | undefined
  >('login');
  const [activeSearchText, setActiveSearchText] = useState<string[]>([]);
  const [unusedSearchText, setUnusedSearchText] = useState<string[]>(
    defaultStrings,
  );

  const loadPopular = () => {
    const { retrievePopular } = props;

    if (!props.loading) {
      retrievePopular({
        itemTypes: ['movie'],
        limit: 10,
      });
    }
  };

  useEffect(() => {
    const { isLoggedIn, userSelf } = props;

    loadPopular();

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);

    if (isLoggedIn && userSelf && userSelf.user && userSelf.user.uid) {
      ReactGA.set({ userId: userSelf.user.uid });
    }

    setNumberMovies(488689);
  }, []);

  // This is the hooks replacement for async setState
  // when unusedSearchText state changes, run setSearchText()
  useEffect(() => {
    setSearchText();
  }, [unusedSearchText]);

  const setSearchText = (): void => {
    const randIndex: number = Number(
      Math.floor(Math.random() * unusedSearchText.length),
    );
    const chosenText: string[] = [unusedSearchText[randIndex]];

    setActiveSearchText(chosenText);
  };

  const resetSearchText = (usedString): void => {
    const newUnused = _.filter(unusedSearchText, item => {
      return item !== usedString;
    });

    if (unusedSearchText.length === 1) {
      setUnusedSearchText(defaultStrings);
    } else {
      setUnusedSearchText(newUnused);
    }
  };

  const toggleAuthModal = (initialForm?: 'login' | 'signup') => {
    if (['xs', 'sm', 'md'].includes(props.width)) {
      setAuthModalOpen(false);
      setAuthModalScreen(undefined);
      props.history.push(`/${initialForm}`);
    } else {
      setAuthModalOpen(!authModalOpen);
      setAuthModalScreen(initialForm);
    }
  };

  const renderTotalMoviesSection = () => {
    const { popular, userSelf, thingsBySlug } = props;

    return (
      <div className={classes.container}>
        <div className={classes.gridContainer}>
          <Grid container spacing={1}>
            {popular &&
              popular.slice(0, 4).map(result => {
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
              className={classes.highlightText}
            >
              <Odometer
                value={numberMovies}
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
              onClick={() => toggleAuthModal('signup')}
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
              onClick={() => toggleAuthModal('login')}
              className={classes.loginButton}
            >
              <ExitToApp className={classes.buttonIcon} />
              Login
            </Button>
          </div>
        </div>
      </div>
    );
  };

  const renderSearchSection = () => {
    return (
      <div className={classes.searchContainer}>
        <div className={classes.searchCtaContainer}>
          <div className={classes.movieCountContainer}>
            <Typography
              color="secondary"
              variant="h2"
              className={classes.highlightText}
            >
              Discovery
            </Typography>
            <Typography variant="h2"> made easy.</Typography>
          </div>
          <Typography variant="h4" color="textSecondary">
            {activeSearchText && activeSearchText.length > 0 && (
              <Typist
                key={unusedSearchText.length}
                onTypingDone={() => resetSearchText(activeSearchText[0])}
              >
                <Typist.Delay ms={1000} />
                {activeSearchText[0]}
                <Typist.Backspace
                  count={activeSearchText[0].length}
                  delay={1250}
                />
              </Typist>
            )}
          </Typography>
          <div className={classes.buttonContainer}>
            <Button
              size="small"
              variant="outlined"
              aria-label="Signup"
              onClick={() => toggleAuthModal('signup')}
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
              onClick={() => toggleAuthModal('login')}
              className={classes.loginButton}
            >
              <ExitToApp className={classes.buttonIcon} />
              Login
            </Button>
          </div>
        </div>
      </div>
    );
  };

  const renderListSection = () => {
    const { popular, thingsBySlug, userSelf } = props;

    return (
      <div className={classes.listContainer}>
        <div className={classes.posterContainer}>
          <Grid container spacing={2}>
            {popular &&
              popular.slice(4, 10).map(result => {
                let thing = thingsBySlug[result];
                return (
                  <ItemCard
                    key={result}
                    userSelf={userSelf}
                    item={thing}
                    gridProps={{ xs: 4, sm: 4, md: 2, lg: 2 }}
                    hoverAddToList={false}
                    hoverDelete={false}
                    hoverWatch={false}
                  />
                );
              })}
          </Grid>
        </div>
        <div className={classes.listCtaContainer}>
          <div className={classes.listTitleContainer}>
            <Typography
              color="secondary"
              variant="h2"
              className={classes.highlightText}
            >
              Lists
            </Typography>
            <Typography variant="h2"> have never been so fun.</Typography>
          </div>
          <div className={classes.buttonContainer}>
            <Button
              size="large"
              variant="outlined"
              aria-label="Signup"
              onClick={() => toggleAuthModal('signup')}
              className={classes.signupButton}
            >
              <List className={classes.buttonIcon} />
              Create a List
            </Button>
          </div>
        </div>
      </div>
    );
  };

  return !props.isAuthed ? (
    <React.Fragment>
      <div className={classes.wrapper}>
        {renderTotalMoviesSection()}
        <Divider />
        {renderListSection()}
        <Divider />
        {renderSearchSection()}
        <Divider />
      </div>
      <AuthDialog
        open={authModalOpen}
        onClose={() => toggleAuthModal()}
        initialForm={authModalScreen}
      />
    </React.Fragment>
  ) : (
    <Redirect to="/" />
  );
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
    withRouter(
      connect(
        mapStateToProps,
        mapDispatchToProps,
      )(Home),
    ),
    () => null,
  ),
);
