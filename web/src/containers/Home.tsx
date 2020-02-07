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
import React, { useEffect, useRef, useState } from 'react';
import ReactGA from 'react-ga';
import { connect } from 'react-redux';
import { Redirect, RouteComponentProps, withRouter } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { Item } from '../types/v2/Item';
import dynamic from 'next/dynamic';
const Odometer = dynamic(() => import('react-odometerjs'), {
  ssr: false,
});
import Typist from 'react-typist';
// import 'react-typist/dist/Typist.css';
// import 'odometer/themes/odometer-theme-default.css';
import AuthDialog from '../components/Auth/AuthDialog';
import { retrievePopular } from '../actions/popular';
import { PopularInitiatedActionPayload } from '../actions/popular/popular';
import ItemCard from '../components/ItemCard';
import _ from 'lodash';
import useIntersectionObserver from '../hooks/useIntersectionObserver';

const useStyles = makeStyles((theme: Theme) => ({
  layout: {
    display: 'flex',
    flexDirection: 'column',
  },
  buttonContainer: {
    display: 'flex',
    flexDirection: 'row',
  },
  buttonIcon: {
    marginRight: theme.spacing(1),
  },
  container: {
    display: 'flex',
    flexDirection: 'row',
    margin: theme.spacing(3),
    alignItems: 'center',
    justifyContent: 'center',
    [theme.breakpoints.down('sm')]: {
      flexDirection: 'column-reverse',
      margin: theme.spacing(0.5),
    },
  },
  ctaContainer: {
    display: 'flex',
    flexDirection: 'column',
    margin: theme.spacing(12),
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
    margin: theme.spacing(4, 0),
    [theme.breakpoints.down('sm')]: {
      margin: theme.spacing(6, 0),
    },
  },
  listTitleContainer: {
    display: 'flex',
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'center',
    width: '100%',
    margin: theme.spacing(2, 0),
    [theme.breakpoints.down('sm')]: {
      flexDirection: 'column',
      textAlign: 'center',
    },
  },
  loginButton: {
    margin: theme.spacing(2, 2, 2, 1),
    width: '100%',
    maxWidth: 200,
    whiteSpace: 'nowrap',
  },
  highlightText: {
    marginRight: theme.spacing(2),
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
    margin: theme.spacing(3),
    filter: 'blur(3px)',
  },
  searchContainer: {
    display: 'flex',
    flexGrow: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    margin: theme.spacing(3),
    [theme.breakpoints.down('sm')]: {
      flexDirection: 'column-reverse',
      margin: theme.spacing(3, 0),
    },
  },
  searchCtaContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    margin: theme.spacing(12),
    [theme.breakpoints.down('sm')]: {
      margin: 0,
      textAlign: 'center',
    },
  },
  signupButton: {
    margin: theme.spacing(2, 1, 2, 0),
    width: '100%',
    maxWidth: 200,
    whiteSpace: 'nowrap',
  },
}));

interface InjectedProps {
  isAuthed: boolean;
  loading: boolean;
  thingsById: { [key: string]: Item };
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
  const loadWrapperRef = useRef(null);
  const isInViewport = useIntersectionObserver({
    lazyLoadOptions: {
      root: null,
      rootMargin: '50px',
      threshold: 0,
    },
    targetRef: loadWrapperRef,
    useLazyLoad: true,
  });

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

    ReactGA.pageview(window.location.pathname + window.location.search);

    if (
      isLoggedIn &&
      userSelf &&
      userSelf.user &&
      userSelf.user.getUsername()
    ) {
      ReactGA.set({ userId: userSelf.user.getUsername() });
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
    const { popular, userSelf, thingsById } = props;

    return (
      <div className={classes.container}>
        <div className={classes.gridContainer}>
          <Grid container spacing={1}>
            {popular &&
              popular.slice(0, 4).map(result => {
                let thing = thingsById[result];
                return (
                  <ItemCard
                    key={result}
                    userSelf={userSelf}
                    item={thing}
                    gridProps={{ xs: 3, sm: 3, md: 6, lg: 6 }}
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
                // @ts-ignore
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
    const { popular, thingsById, userSelf } = props;

    return (
      <div className={classes.listContainer}>
        <div className={classes.posterContainer}>
          <Grid container spacing={2}>
            {popular &&
              popular.slice(4, 10).map(result => {
                let thing = thingsById[result];
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
      <div className={classes.layout}>
        {renderTotalMoviesSection()}
        <Divider />
        {renderListSection()}
        <div ref={loadWrapperRef}>
          <Divider />
          {isInViewport && renderSearchSection()}
        </div>
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
    thingsById: appState.itemDetail.thingsById,
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
    withRouter(connect(mapStateToProps, mapDispatchToProps)(Home)),
    () => null,
  ),
);
