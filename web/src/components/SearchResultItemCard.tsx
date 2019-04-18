import {
  Button,
  Card,
  CardContent,
  CardMedia,
  createStyles,
  Grid,
  Icon,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import React, { Component } from 'react';
import Truncate from 'react-truncate';
import AddToListDialog from '../components/AddToListDialog';
import { User } from '../types';
import { Thing } from '../types/external/themoviedb/Movie';
import { getDescription, getPosterPath } from '../utils/metadata-access';

const styles = (theme: Theme) =>
  createStyles({
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

interface SearchResultItemProps extends WithStyles<typeof styles> {
  item: Thing;
  userSelf?: User;
}

interface SearchResultItemState {
  modalOpen: boolean;
}

class SearchResultItemComponent extends Component<
  SearchResultItemProps,
  SearchResultItemState
> {
  state: SearchResultItemState = {
    modalOpen: false,
  };

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

  handleModalOpen = (item: Thing) => {
    this.setState({ modalOpen: true });
  };

  handleModalClose = () => {
    this.setState({ modalOpen: false });
  };

  render() {
    let { item, classes } = this.props;
    return (
      <React.Fragment>
        <Grid key={item.id} sm={6} md={4} lg={3} item>
          <Card className={classes.card}>
            {this.renderPoster(item)}
            <CardContent className={classes.cardContent}>
              <Typography
                className={classes.title}
                gutterBottom
                variant="h5"
                component="h2"
              >
                {item.name}
              </Typography>
              <Typography>
                <Truncate lines={3} ellipsis={<span>...</span>}>
                  {getDescription(item)}
                </Truncate>
              </Typography>
              <Button
                variant="contained"
                color="primary"
                onClick={() => this.handleModalOpen(item)}
              >
                <Icon>playlist_add</Icon>
                <Typography color="inherit">Add to List</Typography>
              </Button>
            </CardContent>
          </Card>
        </Grid>
        <AddToListDialog
          open={this.state.modalOpen}
          userSelf={this.props.userSelf!}
          item={item}
        />
      </React.Fragment>
    );
  }
}

export default withStyles(styles)(SearchResultItemComponent);
