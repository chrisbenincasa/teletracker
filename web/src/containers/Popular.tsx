import {
  Button,
  ButtonGroup,
  createStyles,
  Grid,
  LinearProgress,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import {
  Link as RouterLink,
  RouteComponentProps,
  withRouter,
} from 'react-router-dom';
import * as R from 'ramda';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { retrievePopular } from '../actions/popular';
import { getMetadataPath } from '../utils/metadata-access';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import { PopularInitiatedActionPayload } from '../actions/popular/popular';
import Featured from '../components/Featured';
import Thing from '../types/Thing';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    filterButtons: {
      [theme.breakpoints.down('sm')]: {
        fontSize: '0.575rem',
      },
    },
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  isAuthed: boolean;
  isSearching: boolean;
  popular?: string[];
  thingsBySlug: { [key: string]: Thing };
}

interface RouteParams {
  id: string;
}

interface DispatchProps {
  retrievePopular: (payload: PopularInitiatedActionPayload) => void;
}

type Props = OwnProps &
  InjectedProps &
  DispatchProps &
  WithUserProps &
  RouteComponentProps<RouteParams>;

interface State {
  mainItemIndex: number;
  type?: ('movie' | 'show')[];
}

class Popular extends Component<Props, State> {
  state: State = {
    mainItemIndex: -1,
  };

  constructor(props: Props) {
    super(props);
    let params = new URLSearchParams(location.search);
    let type;
    let param = params.get('type');
    if (param === 'movie' || param === 'show') {
      type = [param];
    } else {
      type = undefined;
    }

    this.state = {
      ...this.state,
      type,
    };
  }

  componentDidMount() {
    this.props.retrievePopular({
      itemTypes: this.state.type,
      limit: 19,
    });
  }

  componentDidUpdate(prevProps) {
    const { popular, thingsBySlug } = this.props;
    const { mainItemIndex } = this.state;

    // Grab random item from filtered list of popular movies
    if ((!prevProps.popular && popular) || (popular && mainItemIndex === -1)) {
      const highestRated = popular.filter(item => {
        const thing = thingsBySlug[item];
        const voteAverage = Number(getMetadataPath(thing, 'vote_average')) || 0;
        const voteCount = Number(getMetadataPath(thing, 'vote_count')) || 0;
        return voteAverage > 7 && voteCount > 1000;
      });

      const randomItem = Math.floor(Math.random() * highestRated.length);
      const popularItem = popular.findIndex(
        name => name === highestRated[randomItem],
      );

      this.setState({
        mainItemIndex: popularItem,
      });
    }

    if (
      this.props.location &&
      prevProps.location.search !== this.props.location.search
    ) {
      let params = new URLSearchParams(location.search);
      let type;
      let param = params.get('type');
      if (param === 'movie' || param === 'show') {
        type = [param];
      } else if (!param) {
        type = undefined;
      }

      this.setState(
        {
          type,
        },
        () => {
          this.props.retrievePopular({
            itemTypes: this.state.type,
            limit: 19,
          });
        },
      );
    }
  }

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderPopular = () => {
    const { classes, location, popular, userSelf, thingsBySlug } = this.props;
    const { type } = this.state;

    return popular && popular && popular.length ? (
      <div style={{ padding: 8, margin: 20 }}>
        <div
          style={{ display: 'flex', flexDirection: 'row', marginBottom: 10 }}
        >
          <Typography
            color="inherit"
            variant="h4"
            style={{ marginBottom: 10, flexGrow: 1 }}
          >
            {`Popular ${
              type
                ? type.includes('movie')
                  ? 'Movies'
                  : 'TV Shows'
                : 'Content'
            }`}
          </Typography>
          <ButtonGroup
            variant="contained"
            color="primary"
            aria-label="Filter by All, Movies, or just TV Shows"
          >
            <Button
              color={!type ? 'secondary' : 'primary'}
              component={RouterLink}
              to={{
                pathname: location.pathname,
                search: '',
              }}
              className={classes.filterButtons}
            >
              All
            </Button>
            <Button
              color={type && type.includes('movie') ? 'secondary' : 'primary'}
              component={RouterLink}
              to={'?type=movie'}
              className={classes.filterButtons}
            >
              Movies
            </Button>
            <Button
              color={type && type.includes('show') ? 'secondary' : 'primary'}
              component={RouterLink}
              to={'?type=show'}
              className={classes.filterButtons}
            >
              TV
            </Button>
          </ButtonGroup>
        </div>

        <Grid container spacing={2}>
          {popular.map((result, index) => {
            let thing = thingsBySlug[result];
            if (thing && index !== this.state.mainItemIndex) {
              return <ItemCard key={result} userSelf={userSelf} item={thing} />;
            } else {
              return null;
            }
          })}
        </Grid>
      </div>
    ) : null;
  };

  render() {
    const { mainItemIndex } = this.state;
    const { popular, thingsBySlug } = this.props;

    return popular ? (
      <div style={{ display: 'flex', flexGrow: 1, flexDirection: 'column' }}>
        <Featured featuredItem={thingsBySlug[popular[mainItemIndex]]} />
        {this.renderPopular()}
      </div>
    ) : (
      this.renderLoading()
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    isSearching: appState.search.searching,
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

export default withUser(
  withStyles(styles)(
    withRouter(
      connect(
        mapStateToProps,
        mapDispatchToProps,
      )(Popular),
    ),
  ),
);
