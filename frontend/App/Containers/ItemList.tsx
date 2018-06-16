import React from 'react';
import { SectionList, Text, TouchableHighlight, View } from 'react-native';
import Search from 'react-native-search-box';
import { Card, ListItem, Icon, Header } from 'react-native-elements';
import { connect, Dispatch } from 'react-redux';
import { Navigation } from 'react-native-navigation';
import UserActions from '../Redux/UserRedux';
import styles from './Styles/ItemListStyle';

// More info here: https://facebook.github.io/react-native/docs/sectionlist.html

// Styles
class ItemList extends React.PureComponent  {

  /* ***********************************************************
  * STEP 1
  * This is an array of objects with the properties you desire
  * Usually this should come from Redux mapStateToProps
  *************************************************************/
  state = {
    data: [
      {
        key: 'Movies',
        data: [
          {title: 'The Fate of the Furious', platform: 'iTunes', type: 'movie'},
          {title: 'Patti Cake$$$$$', platform: 'HBO', type: 'movie'}
        ]
      }, {
        key: 'TV',
        data: [
          {title: 'Halt & Catch Fire', platform: 'Netflix', type: 'tv'},
          {title: 'Hamilton\'s Pharmacopeia', platform: 'Vice', type: 'tv'}
        ]
      }
    ]
  };

  componentWillMount() {
    this.props.loadUserSelf();
  }


  /* ***********************************************************
  * STEP 3
  * `renderItem` function - How each cell should be rendered
  * It's our best practice to place a single component here:
  *
  * e.g.
  *   return <MyCustomCell title={item.title} platform={item.platform} />
  *
  * For sections with different cells (heterogeneous lists), you can do branch
  * logic here based on section.key OR at the data level, you can provide
  * `renderItem` functions in each section.
  *
  * Note: You can remove section/separator functions and jam them in here
  *************************************************************/
  goToItemDetail() {
    Navigation.push(this.props.componentId, {
      component: {
        name: 'navigation.main.ItemDetailScreen',
        options: {
          topBar: {
            visible: false
          }
        }
      }
    })
  }

  renderItem ({section, item}) {
    return (
        <ListItem 
          key={this.keyExtractor}
          title={item.title}
          leftIcon={{name: item.type}}
          subtitle={item.platform}
          onPress={this.goToItemDetail.bind(this)} 
        />
    )
  }

    // Important: You must return a Promise
    beforeFocus = () => {
      return new Promise((resolve, reject) => {
        console.log('beforeFocus');
        resolve();
      });
    }

    // Important: You must return a Promise
    onFocus = (text) => {
      return new Promise((resolve, reject) => {
        console.log('onFocus', text);
        resolve();
      });
    }

    // Important: You must return a Promise
    afterFocus = () => {
      return new Promise((resolve, reject) => {
        console.log('afterFocus');
        resolve();
      });
    }

  // Conditional branching for section headers, also see step 3
  renderSectionHeader ({section}) {
    switch (section.key) {
      case 'Movies':
        return <View style={styles.sectionHeader}><Text style={styles.boldLabel}>Movies</Text></View>
      default:
        return <View style={styles.sectionHeader}><Text style={styles.boldLabel}>TV Shows</Text></View>
    }
  }

  /* ***********************************************************
  * STEP 2
  * Consider the configurations we've set below.  Customize them
  * to your liking!  Each with some friendly advice.
  *
  * Removing a function here will make SectionList use default
  *************************************************************/

  // Show this when data is empty
  renderEmpty = () =>
    <Text style={styles.label}> Empty list, yo! </Text>;


  // The default function if no Key is provided is index
  // an identifiable key is important if you plan on
  // item reordering.  Otherwise index is fine
  keyExtractor = (item, index) => index;

  // How many items should be kept im memory as we scroll?
  oneScreensWorth = 20;

  // extraData is for anything that is not indicated in data
  // for instance, if you kept "favorites" in `this.state.favs`
  // pass that in, so changes in favorites will cause a re-render
  // and your renderItem will have access to change depending on state
  // e.g. `extraData`={this.state.favs}

  // Optimize your list if the height of each item can be calculated
  // by supplying a constant height, there is no need to measure each
  // item after it renders.  This can save significant time for lists
  // of a size 100+
  // e.g. itemLayout={(data, index) => (
  //   {length: ITEM_HEIGHT, offset: ITEM_HEIGHT * index, index}
  // )}
  
  render () {
    return (
      <View style={styles.container}>
      <Header
        leftComponent={{ icon: 'menu', color: '#fff' }}
        centerComponent={{ text: 'My List', style: { color: '#fff' } }}
        rightComponent={{ icon: 'home', color: '#fff' }}
      />
       <Search
          ref="search_box"
          backgroundColor='white'
          style={{flex:1}}
          /**
          * There are many props options:
          * https://github.com/agiletechvn/react-native-search-box
          */
        />
        <Text style={styles.username}>{this.props.user && this.props.user.name ? this.props.user.name : 'Fetching...'}</Text>
        <Card>
          <SectionList
            renderSectionHeader={this.renderSectionHeader}
            sections={this.state.data}
            contentContainerStyle={styles.listContent}
            data={this.state.dataObjects}
            renderItem={this.renderItem.bind(this)}
            keyExtractor={this.keyExtractor}
            initialNumToRender={this.oneScreensWorth}
            ListHeaderComponent={this.renderHeader}
            ListEmptyComponent={this.renderEmpty}
          />
        </Card>
      </View>
    )
  }
};

const mapStateToProps = (state) => {
  return {
    user: state.user
  }
};

const mapDispatchToProps = (dispatch: Dispatch<any>) => {
  return {
    loadUserSelf: () => {
      dispatch(UserActions.userSelfRequest());
    }
  }
};

export default connect(mapStateToProps, mapDispatchToProps)(ItemList);
