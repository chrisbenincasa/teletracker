import {
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
import * as R from 'ramda';
import { AppState } from '../reducers';
import { layoutStyles } from '../styles';
import { retrievePopular } from '../actions/popular';
import { getMetadataPath } from '../utils/metadata-access';
import ItemCard from '../components/ItemCard';
import withUser, { WithUserProps } from '../components/withUser';
import Featured from '../components/Featured';
import Thing from '../types/Thing';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
  });

interface OwnProps extends WithStyles<typeof styles> {}

interface InjectedProps {
  isAuthed: boolean;
  isSearching: boolean;
  popular?: string[];
  thingsBySlug: { [key: string]: Thing };
}

interface DispatchProps {
  retrievePopular: () => any;
}

type Props = OwnProps & InjectedProps & DispatchProps & WithUserProps;

interface State {
  mainItemIndex: number;
}

class Popular extends Component<Props, State> {
  state: State = {
    mainItemIndex: -1,
  };

  componentDidMount() {
    this.props.retrievePopular();
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
  }

  renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  renderPopular = () => {
    const { popular, userSelf, thingsBySlug } = this.props;

    return popular && popular && popular.length ? (
      <div style={{ padding: 8, margin: 20 }}>
        <Typography color="inherit" variant="h4" style={{ marginBottom: 10 }}>
          Popular Movies
        </Typography>
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
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(Popular),
  ),
);
