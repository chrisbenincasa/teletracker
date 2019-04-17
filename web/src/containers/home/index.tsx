import {
  createStyles,
  CssBaseline,
  Theme,
  Typography,
  WithStyles,
  withStyles,
  Grid,
  CardMedia,
  Card,
  CardContent,
} from '@material-ui/core';
import classNames from 'classnames';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { Redirect } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import { AppState } from '../../reducers';
import { Thing } from '../../types/external/themoviedb/Movie';
import { getPosterPath, getDescription } from '../../utils/metadata-access';
import Truncate from 'react-truncate';
import { layoutStyles } from '../../styles';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    cardGrid: {
      padding: `${theme.spacing.unit * 8}px 0`,
    },
    title: {
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
    },
    card: {
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
    },
    cardMedia: {
      height: 0,
      width: '100%',
      paddingTop: '150%',
    },
    cardContent: {
      flexGrow: 1,
    },
  });

interface Props extends WithStyles<typeof styles> {
  isAuthed: boolean;
  searchResults?: Thing[];
}

class Home extends Component<Props> {
  renderPoster = (thing: Thing) => {
    let poster = getPosterPath(thing);
    if (poster) {
      return (
        <CardMedia
          className={this.props.classes.cardMedia}
          image={'https://image.tmdb.org/t/p/w300' + poster}
          title={thing.name}
        />
      );
    } else {
      return null;
    }
  };

  render() {
    let { searchResults, classes } = this.props;
    searchResults = searchResults || [];

    return this.props.isAuthed ? (
      <main>
        <CssBaseline />
        {searchResults.length ? (
          <div className={classNames(classes.layout, classes.cardGrid)}>
            <Grid container spacing={16}>
              {searchResults.map(result => {
                return (
                  <Grid key={result.id} sm={6} md={4} lg={3} item>
                    <Card className={this.props.classes.card}>
                      {this.renderPoster(result)}
                      <CardContent className={classes.cardContent}>
                        <Typography
                          className={classes.title}
                          gutterBottom
                          variant="h5"
                          component="h2"
                        >
                          {result.name}
                        </Typography>
                        <Typography>
                          <Truncate lines={3} ellipsis={<span>...</span>}>
                            {getDescription(result)}
                          </Truncate>
                        </Typography>
                      </CardContent>
                    </Card>
                  </Grid>
                );
              })}
            </Grid>
          </div>
        ) : null}
      </main>
    ) : (
      <Redirect to="/login" />
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    searchResults: R.path<Thing[]>(['search', 'results', 'data'], appState),
  };
};

const mapDispatchToProps = dispatch => bindActionCreators({}, dispatch);

export default withStyles(styles)(
  connect(
    mapStateToProps,
    mapDispatchToProps,
  )(Home),
);
